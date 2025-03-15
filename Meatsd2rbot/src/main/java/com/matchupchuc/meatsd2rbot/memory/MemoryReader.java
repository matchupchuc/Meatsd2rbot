package com.matchupchuc.meatsd2rbot.memory;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;

public class MemoryReader {
    private static final int PROCESS_VM_READ = 0x0010;
    private static final int PROCESS_QUERY_INFORMATION = 0x0400;
    private WinNT.HANDLE processHandle;

    public MemoryReader() {
        String pidStr = System.getProperty("d2r.pid");
        if (pidStr == null) {
            throw new IllegalStateException("D2R PID not set. Use -Dd2r.pid=<pid> or set via System.setProperty.");
        }
        int pid = Integer.parseInt(pidStr);
        processHandle = Kernel32.INSTANCE.OpenProcess(PROCESS_VM_READ | PROCESS_QUERY_INFORMATION, false, pid);
        if (processHandle == null) {
            throw new RuntimeException("Failed to open D2R process with PID: " + pid);
        }
    }

    public float getPlayerX() {
        // Placeholder for actual memory address (replace with real D2R offset)
        long baseAddress = 0x7d78ee480f90L; // Example offset, adjust based on your memory research
        float x = readFloat(baseAddress);
        return x;
    }

    public float getPlayerY() {
        // Placeholder for actual memory address (replace with real D2R offset)
        long baseAddress = 0x7d78ee480f94L; // Example offset, adjust based on your memory research
        float y = readFloat(baseAddress);
        return y;
    }

    private float readFloat(long address) {
        if (processHandle == null) return 0.0f;
        Memory buffer = new Memory(4);
        if (Kernel32.INSTANCE.ReadProcessMemory(processHandle, Pointer.createConstant(address), buffer, 4, null)) {
            return buffer.getFloat(0);
        }
        return 0.0f;
    }

    public void close() {
        if (processHandle != null) {
            Kernel32.INSTANCE.CloseHandle(processHandle);
            processHandle = null;
        }
    }
}