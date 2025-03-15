package com.matchupchuc.meatsd2rbot.input;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.Pointer;

public class InputController {
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int INPUT_KEYBOARD = 1;
    private WinDef.HWND hwnd;

    public InputController() {
        // Find the D2R window
        hwnd = User32.INSTANCE.FindWindow(null, "Diablo II: Resurrected");
        if (hwnd == null) {
            throw new RuntimeException("Diablo II: Resurrected window not found.");
        }
        System.out.println("Found D2R window handle: " + hwnd);
    }

    public void click(int x, int y) {
        long lParam = makeLParam(x, y);
        User32.INSTANCE.SendMessage(hwnd, WM_LBUTTONDOWN, new WinDef.WPARAM(0), new WinDef.LPARAM(lParam));
        User32.INSTANCE.SendMessage(hwnd, WM_LBUTTONUP, new WinDef.WPARAM(0), new WinDef.LPARAM(lParam));
        System.out.println("Sent click to D2R at (" + x + ", " + y + ")");
    }

    public void sendKey(int keyCode) {
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(INPUT_KEYBOARD);
        input.input.setType("ki");
        WinUser.KEYBDINPUT kbInput = input.input.ki;
        kbInput.wVk = new WinDef.WORD((short) keyCode);
        kbInput.wScan = new WinDef.WORD(0);
        kbInput.dwFlags = new WinDef.DWORD(0); // Key down
        kbInput.time = new WinDef.DWORD(0);
        kbInput.dwExtraInfo = new BaseTSD.ULONG_PTR(0L); // Corrected to use ULONG_PTR with long value
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), new WinUser.INPUT[]{input}, input.size());

        kbInput.dwFlags = new WinDef.DWORD(2); // Key up
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), new WinUser.INPUT[]{input}, input.size());
        System.out.println("Sent key to D2R: " + keyCode);
    }

    private long makeLParam(int x, int y) {
        return ((long) y << 16) | (x & 0xFFFF);
    }
}