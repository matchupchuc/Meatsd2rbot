package com.matchupchuc.meatsd2rbot.memory;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.Pointer;
import com.sun.jna.Memory;

public class MemoryReader {
    private WinNT.HANDLE processHandle;
    private long baseAddress;

    public MemoryReader() {
        String pidStr = System.getProperty("d2r.pid");
        if (pidStr == null) {
            throw new IllegalStateException("D2R process ID not set. Please set the 'd2r.pid' system property.");
        }

        int pid = Integer.parseInt(pidStr);
        processHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_VM_READ | WinNT.PROCESS_QUERY_INFORMATION, false, pid);
        if (processHandle == null) {
            throw new IllegalStateException("Failed to open D2R process with PID: " + pid);
        }

        // Get the base address of the process using GetModuleInformation
        Psapi.MODULEINFO moduleInfo = new Psapi.MODULEINFO();
        if (!Psapi.INSTANCE.GetModuleInformation(processHandle, null, moduleInfo, moduleInfo.size())) {
            Kernel32.INSTANCE.CloseHandle(processHandle);
            throw new IllegalStateException("Failed to get module information for D2R process.");
        }
        baseAddress = Pointer.nativeValue(moduleInfo.lpBaseOfDll);
    }

    public int getGameState() {
        // Offset confirmed to work for Press Any Button screen (StateTitleScreen = 2)
        long addressLong = baseAddress + 0x29000L; // Keep this offset as it worked
        Pointer address = Pointer.createConstant(addressLong);
        Memory buffer = new Memory(4); // Allocate 4 bytes for the integer
        IntByReference bytesRead = new IntByReference();

        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, address, buffer, 4, bytesRead)) {
            if (bytesRead.getValue() == 4) {
                return buffer.getInt(0);
            }
        }
        return -1; // Indicate failure
    }

    public boolean isCharacterSelectScreen() {
        // Updated: Use Koolo's StateCharacterSelection value
        int expectedState = 3; // StateCharacterSelection = 3 in Koolo
        int currentState = getGameState();
        return currentState == expectedState;
    }

    public float getPlayerX() {
        try {
            // Step 1: Read the player pointer at baseAddress + 0x2028E60
            long pPlayerAddrLong = baseAddress + 0x2028E60L;
            Pointer pPlayerAddr = Pointer.createConstant(pPlayerAddrLong);
            Memory buffer = new Memory(8); // Allocate 8 bytes for the long
            IntByReference bytesRead = new IntByReference();
            if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, pPlayerAddr, buffer, 8, bytesRead)) {
                if (bytesRead.getValue() == 8) {
                    long player = buffer.getLong(0);

                    // Step 2: Read the path pointer at player + 0x38
                    long pPathAddrLong = player + 0x38L;
                    Pointer pPathAddr = Pointer.createConstant(pPathAddrLong);
                    Memory pathBuffer = new Memory(8);
                    if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, pPathAddr, pathBuffer, 8, bytesRead)) {
                        if (bytesRead.getValue() == 8) {
                            long path = pathBuffer.getLong(0);

                            // Step 3: Read the X coordinate at path + 0x02 (uint16_t, 2 bytes)
                            long posXAddrLong = path + 0x02L;
                            Pointer posXAddr = Pointer.createConstant(posXAddrLong);
                            Memory posXBuffer = new Memory(2);
                            if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, posXAddr, posXBuffer, 2, bytesRead)) {
                                if (bytesRead.getValue() == 2) {
                                    int posX = posXBuffer.getShort(0) & 0xFFFF; // Unsigned short
                                    return (float) posX;
                                }
                            }
                        }
                    }
                }
            }
            return -1.0f; // Indicate failure
        } catch (Exception e) {
            e.printStackTrace();
            return -1.0f; // Indicate failure
        }
    }

    public float getPlayerY() {
        try {
            // Step 1: Read the player pointer at baseAddress + 0x2028E60
            long pPlayerAddrLong = baseAddress + 0x2028E60L;
            Pointer pPlayerAddr = Pointer.createConstant(pPlayerAddrLong);
            Memory buffer = new Memory(8); // Allocate 8 bytes for the long
            IntByReference bytesRead = new IntByReference();
            if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, pPlayerAddr, buffer, 8, bytesRead)) {
                if (bytesRead.getValue() == 8) {
                    long player = buffer.getLong(0);

                    // Step 2: Read the path pointer at player + 0x38
                    long pPathAddrLong = player + 0x38L;
                    Pointer pPathAddr = Pointer.createConstant(pPathAddrLong);
                    Memory pathBuffer = new Memory(8);
                    if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, pPathAddr, pathBuffer, 8, bytesRead)) {
                        if (bytesRead.getValue() == 8) {
                            long path = pathBuffer.getLong(0);

                            // Step 3: Read the Y coordinate at path + 0x06 (uint16_t, 2 bytes)
                            long posYAddrLong = path + 0x06L;
                            Pointer posYAddr = Pointer.createConstant(posYAddrLong);
                            Memory posYBuffer = new Memory(2);
                            if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, posYAddr, posYBuffer, 2, bytesRead)) {
                                if (bytesRead.getValue() == 2) {
                                    int posY = posYBuffer.getShort(0) & 0xFFFF; // Unsigned short
                                    return (float) posY;
                                }
                            }
                        }
                    }
                }
            }
            return -1.0f; // Indicate failure
        } catch (Exception e) {
            e.printStackTrace();
            return -1.0f; // Indicate failure
        }
    }

    // Utility method to convert 8 bytes to a long (int64_t)
    private long bytesToLong(byte[] bytes) {
        long value = 0;
        for (int i = 0; i < bytes.length; i++) {
            value |= (bytes[i] & 0xFFL) << (8 * i);
        }
        return value;
    }

    // Utility method to convert 2 bytes to an unsigned short (uint16_t)
    private int bytesToUnsignedShort(byte[] bytes) {
        return (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8);
    }

    public void close() {
        if (processHandle != null) {
            Kernel32.INSTANCE.CloseHandle(processHandle);
            processHandle = null;
        }
    }
}
// File: MemoryReader.java