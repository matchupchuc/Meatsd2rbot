package com.matchupchuc.meatsd2rbot;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

public class WindowManager {
    private static final String WINDOW_TITLE = "Diablo II: Resurrected"; // Window title of D2R
    private static final int CLIENT_WIDTH = 1280;  // Desired client area width
    private static final int CLIENT_HEIGHT = 720;  // Desired client area height
    private static final int MAX_RETRIES = 10;     // Number of retries for finding window, moving, and focusing
    private static final long RETRY_DELAY_MS = 1500; // Delay between retries in milliseconds
    private static final long FOCUS_RETRY_DELAY_MS = 500; // Delay between focus retries

    // Find the D2R window and return its handle (HWND)
    public static WinDef.HWND findD2RWindow() {
        WinDef.HWND hwnd = null;
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            hwnd = User32.INSTANCE.FindWindow(null, WINDOW_TITLE);
            if (hwnd != null) {
                System.out.println("D2R window found: " + hwnd);
                return hwnd;
            }
            System.out.println("Retry " + (retry + 1) + " to find D2R window...");
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.err.println("D2R window not found with title: " + WINDOW_TITLE + " after " + MAX_RETRIES + " retries.");
        return null;
    }

    // Ensure the D2R window is the active window
    private static boolean ensureD2RActive(WinDef.HWND hwnd) {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            // Restore the window if minimized
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                System.out.println("D2R window is not visible. Restoring...");
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                try {
                    Thread.sleep(FOCUS_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            // Bring the window to the top and set it as the foreground window
            User32.INSTANCE.BringWindowToTop(hwnd);
            boolean success = User32.INSTANCE.SetForegroundWindow(hwnd);
            if (!success) {
                System.err.println("SetForegroundWindow failed on attempt " + (retry + 1) + ": " + Native.getLastError());
            }

            // Verify if the window is now the foreground window
            WinDef.HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
            if (foregroundWindow != null && foregroundWindow.equals(hwnd)) {
                System.out.println("D2R window is now the active window.");
                return true;
            }

            System.out.println("Retry " + (retry + 1) + " to set D2R window as active...");
            try {
                Thread.sleep(FOCUS_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        System.err.println("Failed to set D2R window as active after " + MAX_RETRIES + " retries.");
        return false;
    }

    // Resize the window to 1280x720 client area
    public static boolean resizeD2RWindow() {
        WinDef.HWND hwnd = findD2RWindow();
        if (hwnd == null) {
            System.err.println("Failed to find D2R window for resizing. Please ensure the game is running.");
            return false;
        }

        try {
            // Restore the window from maximized or minimized state
            WinUser.WINDOWPLACEMENT placement = new WinUser.WINDOWPLACEMENT();
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                System.out.println("D2R window is not visible. Restoring...");
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                Thread.sleep(500);
            } else {
                WinDef.BOOL placementResult = User32.INSTANCE.GetWindowPlacement(hwnd, placement);
                boolean placementSuccess = placementResult.booleanValue();
                if (placementSuccess && placement.showCmd == WinUser.SW_MAXIMIZE) {
                    System.out.println("D2R window is maximized. Restoring...");
                    User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                    Thread.sleep(500);
                }
            }

            // Get the window style to calculate the adjusted window size
            int style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE);
            System.out.println("Window style: 0x" + Integer.toHexString(style));

            // Start with the desired client area size
            WinDef.RECT adjustedRect = new WinDef.RECT();
            adjustedRect.left = 0;
            adjustedRect.top = 0;
            adjustedRect.right = CLIENT_WIDTH;
            adjustedRect.bottom = CLIENT_HEIGHT;

            // Adjust the rectangle to include non-client area (borders, title bar)
            WinDef.BOOL success = User32.INSTANCE.AdjustWindowRect(adjustedRect, new WinDef.DWORD(style), new WinDef.BOOL(false));
            if (!success.booleanValue()) {
                System.err.println("AdjustWindowRect failed: " + Native.getLastError());
                return false;
            }

            // Calculate the initial total window size including borders and title bar
            int totalWidth = adjustedRect.right - adjustedRect.left;
            int totalHeight = adjustedRect.bottom - adjustedRect.top;
            System.out.println("Initial adjusted window size for 1280x720 client area: " + totalWidth + "x" + totalHeight);

            // Resize the window and iteratively adjust until the client area is 1280x720
            boolean resizeSuccess = false;
            int widthAdjustment = 0;
            int heightAdjustment = 0;
            for (int retry = 0; retry < MAX_RETRIES; retry++) {
                // Ensure the window is active before resizing
                if (!ensureD2RActive(hwnd)) {
                    System.err.println("Failed to set D2R window as active before resizing. Aborting resize attempt " + (retry + 1) + "...");
                    continue;
                }

                // Perform the resize with the adjusted size
                int adjustedTotalWidth = totalWidth + widthAdjustment;
                int adjustedTotalHeight = totalHeight + heightAdjustment;
                System.out.println("Attempting to set window size to: " + adjustedTotalWidth + "x" + adjustedTotalHeight);
                User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, adjustedTotalWidth, adjustedTotalHeight,
                        User32.SWP_SHOWWINDOW | User32.SWP_NOZORDER | User32.SWP_NOMOVE | User32.SWP_ASYNCWINDOWPOS);
                Thread.sleep(RETRY_DELAY_MS);

                // Verify the client area size
                WinDef.RECT clientRect = new WinDef.RECT();
                if (User32.INSTANCE.GetClientRect(hwnd, clientRect)) {
                    int clientWidth = clientRect.right - clientRect.left;
                    int clientHeight = clientRect.bottom - clientRect.top;
                    System.out.println("Client area size after resize attempt " + (retry + 1) + ": " + clientWidth + "x" + clientHeight);

                    // Check if the client area matches the desired size
                    if (clientWidth == CLIENT_WIDTH && clientHeight == CLIENT_HEIGHT) {
                        resizeSuccess = true;
                        System.out.println("D2R window resized to client area 1280x720 successfully.");
                        break; // Exit the loop immediately upon success
                    } else {
                        // Calculate the difference and adjust
                        int widthDiff = CLIENT_WIDTH - clientWidth;
                        int heightDiff = CLIENT_HEIGHT - clientHeight;
                        widthAdjustment += widthDiff;
                        heightAdjustment += heightDiff;
                        System.out.println("Client area size does not match 1280x720. Adjusting by " + widthDiff + "x" + heightDiff + " pixels. Retrying...");
                    }
                } else {
                    System.err.println("Failed to get client rect on resize attempt " + (retry + 1) + ": " + Native.getLastError());
                }
            }

            if (!resizeSuccess) {
                System.err.println("Failed to resize D2R window to 1280x720 client area after " + MAX_RETRIES + " retries.");
                return false;
            }

            // Log the final window dimensions (including borders) for reference
            WinDef.RECT windowRect = new WinDef.RECT();
            if (User32.INSTANCE.GetWindowRect(hwnd, windowRect)) {
                int windowWidth = windowRect.right - windowRect.left;
                int windowHeight = windowRect.bottom - windowRect.top;
                System.out.println("Final window size (including borders): " + windowWidth + "x" + windowHeight);
            } else {
                System.err.println("Failed to get final window rect: " + Native.getLastError());
            }

            // Ensure the window remains active after resizing
            if (!ensureD2RActive(hwnd)) {
                System.err.println("Failed to set D2R window as active after resizing.");
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error in resizeD2RWindow: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Move the window to (0,0)
    public static boolean moveD2RWindow() {
        WinDef.HWND hwnd = findD2RWindow();
        if (hwnd == null) {
            System.err.println("Failed to find D2R window for moving. Please ensure the game is running.");
            return false;
        }

        try {
            // Restore the window from maximized or minimized state
            WinUser.WINDOWPLACEMENT placement = new WinUser.WINDOWPLACEMENT();
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                System.out.println("D2R window is not visible. Restoring...");
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                Thread.sleep(500);
            } else {
                WinDef.BOOL placementResult = User32.INSTANCE.GetWindowPlacement(hwnd, placement);
                boolean placementSuccess = placementResult.booleanValue();
                if (placementSuccess && placement.showCmd == WinUser.SW_MAXIMIZE) {
                    System.out.println("D2R window is maximized. Restoring...");
                    User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                    Thread.sleep(500);
                }
            }

            // Get the current window dimensions (we'll use these to move without resizing)
            WinDef.RECT windowRect = new WinDef.RECT();
            if (!User32.INSTANCE.GetWindowRect(hwnd, windowRect)) {
                System.err.println("Failed to get window rectangle for moving: " + Native.getLastError());
                return false;
            }
            int totalWidth = windowRect.right - windowRect.left;
            int totalHeight = windowRect.bottom - windowRect.top;

            // Move the window to (0,0)
            boolean moveSuccess = false;
            for (int retry = 0; retry < MAX_RETRIES; retry++) {
                // Ensure the window is still visible and focused
                if (!ensureD2RActive(hwnd)) {
                    System.err.println("Failed to set D2R window as active before moving. Aborting move attempt " + (retry + 1) + "...");
                    continue;
                }

                // Ensure the window is in a restored state
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                Thread.sleep(500);

                // Move the window to (0,0)
                boolean setWindowSuccess = User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, totalWidth, totalHeight,
                        User32.SWP_SHOWWINDOW | User32.SWP_NOZORDER | User32.SWP_NOSIZE | User32.SWP_ASYNCWINDOWPOS);
                if (!setWindowSuccess) {
                    System.err.println("SetWindowPos failed on move attempt " + (retry + 1) + ": " + Native.getLastError());
                }

                Thread.sleep(RETRY_DELAY_MS);

                // Verify the window position
                if (User32.INSTANCE.GetWindowRect(hwnd, windowRect)) {
                    int windowLeft = windowRect.left;
                    int windowTop = windowRect.top;
                    System.out.println("Window position after move attempt " + (retry + 1) + ": (" + windowLeft + ", " + windowTop + ")");
                    if (windowLeft == 0 && windowTop == 0) {
                        moveSuccess = true;
                        System.out.println("D2R window moved to (0,0) successfully.");
                        break;
                    }
                } else {
                    System.err.println("Failed to get window rectangle on move attempt " + (retry + 1) + ": " + Native.getLastError());
                }
                System.out.println("Retry " + (retry + 1) + " to move D2R window to (0,0)...");
            }

            if (!moveSuccess) {
                System.err.println("Failed to move D2R window to (0,0) after " + MAX_RETRIES + " retries.");
                return false;
            }

            // Final focus attempt to ensure the window remains active
            if (!ensureD2RActive(hwnd)) {
                System.err.println("Failed to set D2R window as active after moving.");
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error in moveD2RWindow: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}