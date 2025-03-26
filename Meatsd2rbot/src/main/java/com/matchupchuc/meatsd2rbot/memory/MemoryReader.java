package com.matchupchuc.meatsd2rbot.memory;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.Psapi.MODULEINFO;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.WinDef;
import java.util.Arrays;
import java.util.List;

public class MemoryReader implements AutoCloseable {
    // Custom Psapi interface to define GetModuleBaseNameW
    public interface CustomPsapi extends com.sun.jna.Library {
        CustomPsapi INSTANCE = Native.load("psapi", CustomPsapi.class);

        boolean EnumProcessModules(HANDLE hProcess, WinDef.HMODULE[] lphModule, int cb, IntByReference lpcbNeeded);

        int GetModuleBaseNameW(HANDLE hProcess, WinDef.HMODULE hModule, char[] lpBaseName, int nSize);

        boolean GetModuleInformation(HANDLE hProcess, WinDef.HMODULE hModule, MODULEINFO lpmodinfo, int cb);
    }

    // Custom Kernel32 interface for VirtualQueryEx
    public interface CustomKernel32 extends com.sun.jna.Library {
        CustomKernel32 INSTANCE = Native.load("kernel32", CustomKernel32.class);

        HANDLE OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);

        boolean ReadProcessMemory(HANDLE hProcess, Pointer lpBaseAddress, Memory lpBuffer, int nSize, IntByReference lpNumberOfBytesRead);

        boolean CloseHandle(HANDLE hObject);

        int VirtualQueryEx(HANDLE hProcess, Pointer lpAddress, MEMORY_BASIC_INFORMATION lpBuffer, int dwLength);
    }

    // MEMORY_BASIC_INFORMATION structure
    public static class MEMORY_BASIC_INFORMATION extends com.sun.jna.Structure {
        public Pointer BaseAddress;
        public Pointer AllocationBase;
        public int AllocationProtect;
        public short PartitionId;
        public long RegionSize;
        public int State;
        public int Protect;
        public int Type;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("BaseAddress", "AllocationBase", "AllocationProtect", "PartitionId", "RegionSize", "State", "Protect", "Type");
        }
    }

    private HANDLE processHandle;
    private long baseAddress;
    private long moduleSize;
    private long stateAddress = -1; // Cache the state address
    private int readFailureCount = 0;
    private static final int MAX_FAILURES = 5;
    private boolean searchOnlyForState531 = false; // Flag to search only for state 531

    // State patterns
    private static final byte[] STATE_ADDRESS_PATTERN_531 = new byte[] { 0x13, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }; // State 531 (Character Select)
    private static final byte[] STATE_ADDRESS_PATTERN_14 = new byte[] { 0x0E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };  // State 14 (Cinematic)
    private static final byte[] STATE_ADDRESS_PATTERN_590 = new byte[] { 0x4E, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }; // State 590 (Difficulty Menu)
    private static final String STATE_ADDRESS_PATTERN_MASK = "xxxx????";
    private static final int STATE_ADDRESS_OFFSET = 0;

    public MemoryReader() throws Exception {
        initialize();
    }

    private void initialize() throws Exception {
        // Get the D2R PID from system property
        String pidStr = System.getProperty("d2r.pid");
        if (pidStr == null) {
            throw new Exception("D2R PID not set in system properties.");
        }

        long pid = Long.parseLong(pidStr);
        processHandle = CustomKernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_VM_READ | WinNT.PROCESS_QUERY_INFORMATION,
                false,
                (int) pid
        );

        if (processHandle == null) {
            throw new Exception("Failed to open D2R process with PID " + pid + ". Error code: " + Native.getLastError());
        }

        // Get the base address of D2R.exe module
        long[] baseInfo = getBaseAddress(pid, "D2R.exe");
        baseAddress = baseInfo[0];
        moduleSize = baseInfo[1];
        if (baseAddress == 0) {
            close();
            throw new Exception("Failed to find D2R.exe base address.");
        }
    }

    private long[] getBaseAddress(long pid, String moduleName) throws Exception {
        // Use EnumProcessModules to get the list of modules
        WinDef.HMODULE[] hMods = new WinDef.HMODULE[1024];
        IntByReference lpcbNeeded = new IntByReference();

        if (!CustomPsapi.INSTANCE.EnumProcessModules(processHandle, hMods, hMods.length * Native.POINTER_SIZE, lpcbNeeded)) {
            throw new Exception("EnumProcessModules failed: " + Native.getLastError());
        }

        int needed = lpcbNeeded.getValue() / Native.POINTER_SIZE;
        for (int i = 0; i < needed && i < hMods.length; i++) {
            if (hMods[i] == null) {
                continue;
            }

            // Get the module name
            char[] moduleNameBuf = new char[1024];
            int length = CustomPsapi.INSTANCE.GetModuleBaseNameW(processHandle, hMods[i], moduleNameBuf, moduleNameBuf.length);
            if (length == 0) {
                continue;
            }

            String modName = new String(moduleNameBuf, 0, length).toLowerCase();
            if (modName.equals(moduleName.toLowerCase())) {
                // Get the module information
                MODULEINFO moduleInfo = new MODULEINFO();
                if (CustomPsapi.INSTANCE.GetModuleInformation(processHandle, hMods[i], moduleInfo, moduleInfo.size())) {
                    long baseAddr = Pointer.nativeValue(moduleInfo.lpBaseOfDll);
                    long modSize = moduleInfo.SizeOfImage;
                    // Cap module size to prevent excessive scanning
                    long MAX_MODULE_SIZE = 0x10000000L;
                    if (modSize > MAX_MODULE_SIZE) {
                        System.err.println("Reported module size " + Long.toHexString(modSize) + " seems too large. Capping at " + Long.toHexString(MAX_MODULE_SIZE) + ".");
                        modSize = MAX_MODULE_SIZE;
                    }
                    return new long[]{baseAddr, modSize};
                }
            }
        }
        return new long[]{0, 0};
    }

    // Method to set the search mode
    public void setSearchOnlyForState531(boolean searchOnly) {
        this.searchOnlyForState531 = searchOnly;
        if (searchOnly) {
            // Clear the cached state address to force a fresh search for state 531
            this.stateAddress = -1;
        }
    }

    public long findStateAddress(byte[] statePattern) throws Exception {
        final long MEM_COMMIT = 0x1000;
        final long PAGE_READWRITE = 0x04;
        long address = 0; // Start from the beginning of the address space
        MEMORY_BASIC_INFORMATION mbi = new MEMORY_BASIC_INFORMATION();
        int patternLength = statePattern.length;

        System.err.println("Enumerating memory regions from 0x0 to maximum addressable space...");
        // On 64-bit systems, the user-mode address space is typically up to 0x7FFFFFFFFFFF
        long MAX_ADDRESS = 0x7FFFFFFFFFFFL;
        while (address < MAX_ADDRESS) {
            int size = CustomKernel32.INSTANCE.VirtualQueryEx(processHandle, Pointer.createConstant(address), mbi, mbi.size());
            if (size == 0) {
                int errorCode = Native.getLastError();
                if (errorCode == 998) { // ERROR_NOACCESS
                    address += 0x1000; // Skip to the next page
                    continue;
                } else {
                    System.err.println("VirtualQueryEx failed at address " + Long.toHexString(address) + " with error code: " + errorCode);
                    break;
                }
            }

            if (mbi.State == MEM_COMMIT && (mbi.Protect & PAGE_READWRITE) != 0) {
                long startAddress = Pointer.nativeValue(mbi.BaseAddress);
                long endAddress = startAddress + mbi.RegionSize;
                System.err.println("Searching in region " + Long.toHexString(startAddress) + " to " + Long.toHexString(endAddress) + " (Size: " + Long.toHexString(mbi.RegionSize) + ")");

                long chunkSize = 0x8000;
                for (long start = startAddress; start < endAddress; start += chunkSize) {
                    long end = Math.min(start + chunkSize, endAddress);
                    long sizeToRead = end - start;
                    Memory memory = readMemory(start, (int) sizeToRead);
                    if (memory == null) {
                        continue;
                    }

                    for (int i = 0; i <= memory.size() - patternLength; i++) {
                        boolean match = true;
                        for (int j = 0; j < patternLength; j++) {
                            if (STATE_ADDRESS_PATTERN_MASK.charAt(j) == 'x' && memory.getByte(i + j) != statePattern[j]) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            long foundAddress = start + i + STATE_ADDRESS_OFFSET;
                            System.err.println("Found state address at " + Long.toHexString(foundAddress));
                            int stateValue = readInt(foundAddress);
                            if (stateValue != -1) {
                                System.err.println("State ID found: " + stateValue + " at address " + Long.toHexString(foundAddress));
                                return foundAddress;
                            } else {
                                System.err.println("Failed to read state value at " + Long.toHexString(foundAddress));
                            }
                        }
                    }
                }
            }
            address += mbi.RegionSize > 0 ? mbi.RegionSize : 0x1000; // Ensure we increment the address
        }

        System.err.println("Pattern not found after scanning all accessible memory regions.");
        return -1;
    }

    private Memory readMemory(long address, int size) {
        try {
            Memory buffer = new Memory(size);
            IntByReference bytesRead = new IntByReference();
            boolean success = CustomKernel32.INSTANCE.ReadProcessMemory(
                    processHandle,
                    Pointer.createConstant(address),
                    buffer,
                    size,
                    bytesRead
            );
            if (!success || bytesRead.getValue() != size) {
                System.err.println("Failed to read memory at " + Long.toHexString(address) + ". Success: " + success + ", Bytes read: " + bytesRead.getValue() + ", Error code: " + Native.getLastError());
                return null;
            }
            return buffer;
        } catch (Exception e) {
            System.err.println("Error reading memory at " + Long.toHexString(address) + ": " + e.getMessage());
            return null;
        }
    }

    public int readInt(long address) {
        try {
            Memory buffer = new Memory(4);
            IntByReference bytesRead = new IntByReference();
            boolean success = CustomKernel32.INSTANCE.ReadProcessMemory(
                    processHandle,
                    Pointer.createConstant(address),
                    buffer,
                    4,
                    bytesRead
            );
            if (!success || bytesRead.getValue() != 4) {
                System.err.println("Failed to read memory at " + Long.toHexString(address) + ". Success: " + success + ", Bytes read: " + bytesRead.getValue() + ", Error code: " + Native.getLastError());
                return -1;
            }
            System.err.println("Successfully read integer: " + buffer.getInt(0) + " from " + Long.toHexString(address));
            return buffer.getInt(0);
        } catch (Exception e) {
            System.err.println("Error reading memory at " + Long.toHexString(address) + ": " + e.getMessage());
            return -1;
        }
    }

    public int getGameState() throws Exception {
        // If the state address is not found or we've had too many read failures, re-search
        if (stateAddress == -1 || readFailureCount >= MAX_FAILURES) {
            System.err.println("Searching for state address...");
            if (searchOnlyForState531) {
                // Only search for state 531 (character select screen)
                stateAddress = findStateAddress(STATE_ADDRESS_PATTERN_531);
                if (stateAddress == -1) {
                    System.err.println("Failed to find state 531 address.");
                    readFailureCount++;
                    throw new Exception("Failed to find state 531 address.");
                }
            } else {
                // Normal search: try state 14, then 531, then 590
                stateAddress = findStateAddress(STATE_ADDRESS_PATTERN_14);
                if (stateAddress == -1) {
                    stateAddress = findStateAddress(STATE_ADDRESS_PATTERN_531);
                }
                if (stateAddress == -1) {
                    stateAddress = findStateAddress(STATE_ADDRESS_PATTERN_590);
                }
                if (stateAddress == -1) {
                    System.err.println("Failed to find state address.");
                    readFailureCount++;
                    throw new Exception("Failed to find state address.");
                }
            }
            readFailureCount = 0;
        }

        // Read the game state
        int gameState = readInt(stateAddress);
        if (gameState == -1) {
            readFailureCount++;
            System.err.println("Failed to read game state at " + Long.toHexString(stateAddress) + ". Consecutive failures: " + readFailureCount);
            if (readFailureCount >= MAX_FAILURES) {
                System.err.println("Too many read failures, re-searching for state address on next call...");
                stateAddress = -1;
            }
            throw new Exception("Failed to read game state.");
        }

        // If we're in state 531-only mode, we don't need to clear the address since we're only looking for 531
        if (!searchOnlyForState531 && gameState == 14) {
            System.err.println("Detected transitional state 14 (loading screen). Clearing cached state address to re-scan...");
            stateAddress = -1; // Force a re-scan on the next call
        } else {
            readFailureCount = 0; // Reset failure count on successful read
        }

        return gameState;
    }

    @Override
    public void close() {
        if (processHandle != null) {
            CustomKernel32.INSTANCE.CloseHandle(processHandle);
            processHandle = null;
        }
    }
}