package com.matchupchuc.meatsd2rbot;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

public class OffsetFinder {
    private static final String PROCESS_NAME = "D2R.exe";
    private WinNT.HANDLE processHandle;
    private long baseAddress;
    private long screenStateOffset;

    public OffsetFinder() {
        if (!openProcess()) {
            throw new RuntimeException("Failed to open D2R process.");
        }
        findOffsets();
    }

    // Check if a D2R process is already running
    public static boolean isD2RRunning() {
        return getProcessId() != 0;
    }

    private boolean openProcess() {
        int pid = getProcessId();
        if (pid == 0) {
            System.err.println("D2R process not found.");
            return false;
        }

        processHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_VM_READ | WinNT.PROCESS_VM_WRITE | WinNT.PROCESS_VM_OPERATION | WinNT.PROCESS_QUERY_INFORMATION,
                false,
                pid
        );

        if (processHandle == null) {
            System.err.println("Failed to open D2R process: " + Native.getLastError());
            return false;
        }

        // Get the base address of D2R.exe
        WinDef.HMODULE[] hMods = new WinDef.HMODULE[1024];
        IntByReference cbNeeded = new IntByReference();
        if (Psapi.INSTANCE.EnumProcessModules(processHandle, hMods, hMods.length * 4, cbNeeded)) {
            for (WinDef.HMODULE hMod : hMods) {
                if (hMod == null) continue;
                byte[] moduleName = new byte[1024];
                Psapi.INSTANCE.GetModuleFileNameExA(processHandle, hMod, moduleName, moduleName.length);
                String modPath = Native.toString(moduleName);
                if (modPath != null && modPath.toLowerCase().endsWith(PROCESS_NAME.toLowerCase())) {
                    baseAddress = Pointer.nativeValue(hMod.getPointer());
                    break;
                }
            }
        }

        return baseAddress != 0;
    }

    private static int getProcessId() {
        Tlhelp32.PROCESSENTRY32 pe32 = new Tlhelp32.PROCESSENTRY32();
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        if (snapshot == WinNT.INVALID_HANDLE_VALUE) {
            return 0;
        }

        try {
            if (Kernel32.INSTANCE.Process32First(snapshot, pe32)) {
                do {
                    String processName = Native.toString(pe32.szExeFile);
                    if (PROCESS_NAME.equalsIgnoreCase(processName)) {
                        return pe32.th32ProcessID.intValue();
                    }
                } while (Kernel32.INSTANCE.Process32Next(snapshot, pe32));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return 0;
    }

    private void findOffsets() {
        // Example pattern for screen state (hypothetical, replace with actual pattern from OffsetFinder.py)
        String screenStatePattern = "48 8B 05 ? ? ? ? 48 8B 88";
        screenStateOffset = findOffset(screenStatePattern, 0);
        if (screenStateOffset == 0) {
            System.err.println("Failed to find screen state offset.");
        }
    }

    public long findOffset(String pattern, int offsetAdjustment) {
        // Convert pattern to byte array (e.g., "48 8B 05 ? ? ? ? 48 8B 88")
        String[] patternParts = pattern.split(" ");
        byte[] patternBytes = new byte[patternParts.length];
        boolean[] mask = new boolean[patternParts.length];
        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].equals("?")) {
                patternBytes[i] = 0;
                mask[i] = false;
            } else {
                patternBytes[i] = (byte) Integer.parseInt(patternParts[i], 16);
                mask[i] = true;
            }
        }

        // Scan memory (simplified, scan a reasonable range)
        long startAddress = baseAddress;
        long endAddress = baseAddress + 0x10000000; // Scan 256MB range (adjust as needed)
        long address = startAddress;
        Memory memory = new Memory(4096);

        while (address < endAddress) {
            IntByReference bytesRead = new IntByReference();
            boolean success = Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), memory, (int) memory.size(), bytesRead);
            if (!success) {
                address += memory.size();
                continue;
            }

            byte[] buffer = memory.getByteArray(0, bytesRead.getValue());
            for (int i = 0; i < buffer.length - patternBytes.length; i++) {
                boolean found = true;
                for (int j = 0; j < patternBytes.length; j++) {
                    if (mask[j] && buffer[i + j] != patternBytes[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    long patternAddress = address + i;
                    // Read the relative offset (assuming pattern is "48 8B 05 ? ? ? ?")
                    int relativeOffset = readInt(patternAddress + 3);
                    long finalAddress = patternAddress + 7 + relativeOffset; // RIP-relative addressing
                    return finalAddress + offsetAdjustment;
                }
            }
            address += bytesRead.getValue();
        }
        return 0;
    }

    public int readInt(long address) {
        Memory memory = new Memory(4);
        IntByReference bytesRead = new IntByReference();
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, new Pointer(address), memory, 4, bytesRead)) {
            return memory.getInt(0);
        }
        return 0;
    }

    public void writeInt(long address, int value) {
        Memory memory = new Memory(4);
        memory.setInt(0, value);
        IntByReference bytesWritten = new IntByReference();
        Kernel32.INSTANCE.WriteProcessMemory(processHandle, new Pointer(address), memory, 4, bytesWritten);
    }

    public long getScreenStateOffset() {
        return screenStateOffset;
    }

    public void close() {
        if (processHandle != null) {
            Kernel32.INSTANCE.CloseHandle(processHandle);
        }
    }
}