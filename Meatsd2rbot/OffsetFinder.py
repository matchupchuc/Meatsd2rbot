import win32process
import win32gui
import win32api
import ctypes
import time
import datetime

def find_d2r_process():
    """Find the PID of the D2R process."""
    hwnd = win32gui.FindWindow(None, "Diablo II: Resurrected")
    if hwnd:
        print(f"Found D2R window with title: {win32gui.GetWindowText(hwnd)}")
        tid, pid = win32process.GetWindowThreadProcessId(hwnd)
        return pid, hwnd
    else:
        print("D2R window not found. Listing all windows...")
        def enum_windows():
            def callback(hwnd, extra):
                title = win32gui.GetWindowText(hwnd)
                if title:
                    print(f"Window handle: {hwnd}, Title: {title}")
        win32gui.EnumWindows(callback, None)
        return None, None

def get_base_address(pid):
    """Get the base address of the D2R process."""
    PROCESS_QUERY_INFORMATION = 0x0400
    PROCESS_VM_READ = 0x0010
    process_handle = win32api.OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, False, pid)
    if process_handle:
        print(f"Opened process handle: {process_handle} (type: {type(process_handle)})")
        handle_value = int(process_handle)
        psapi = ctypes.windll.psapi
        modules = (ctypes.c_void_p * 1024)()
        cb_needed = ctypes.c_ulong()
        handle_as_void_p = ctypes.c_void_p(handle_value)
        if psapi.EnumProcessModules(handle_as_void_p, modules, ctypes.sizeof(modules), ctypes.byref(cb_needed)):
            base_address = modules[0]
            win32api.CloseHandle(process_handle)
            return base_address
        else:
            print(f"EnumProcessModules failed with error code: {ctypes.get_last_error()}")
            win32api.CloseHandle(process_handle)
            return 0
    return 0

def read_memory_float(process_handle, address):
    """Read a 4-byte float from the specified address."""
    buffer = ctypes.c_float()
    bytes_read = ctypes.c_ulong()
    handle_value = int(process_handle)
    handle_as_void_p = ctypes.c_void_p(handle_value)
    address_as_void_p = ctypes.c_void_p(address)
    if ctypes.windll.kernel32.ReadProcessMemory(handle_as_void_p, address_as_void_p, ctypes.byref(buffer), ctypes.sizeof(buffer), ctypes.byref(bytes_read)):
        return buffer.value
    return None

def monitor_coordinates(process_handle, base_address, base_address_xy, hwnd):
    """Continuously monitor the provided address for both X and Y coordinates."""
    print(f"Monitoring coordinates. Press Ctrl+C to stop. Using float precision.")
    log_file = "C:/Users/hayes/Documents/GitHub/MeatsD2RBot/coordinates_log.txt"
    with open(log_file, "a") as f:
        f.write(f"Starting coordinate monitoring at {datetime.datetime.now()} (float precision)\n")

    invalid_count = 0
    max_invalid_reads = 5  # Number of invalid reads before skipping
    valid_read_count = 0  # Track number of valid reads
    last_coords = None  # Track last unique (X, Y) pair
    movement_changes = []  # Track unique coordinate changes
    is_reading_x = True  # Alternate between X and Y

    # Wait for game initialization
    print("Waiting 5 seconds for game to initialize...")
    time.sleep(5)

    try:
        while True:
            xy_value = read_memory_float(process_handle, base_address_xy)
            if xy_value is not None and -15000.0 <= xy_value <= 15000.0:
                # Alternate between X and Y
                x_value = xy_value if is_reading_x else last_coords[0] if last_coords else xy_value
                y_value = xy_value if not is_reading_x else last_coords[1] if last_coords else xy_value
                is_reading_x = not is_reading_x

                invalid_count = 0  # Reset counter on valid read
                valid_read_count += 1

                # Track unique coordinate changes (more than 1 unit difference)
                current_coords = (x_value, y_value)
                if last_coords is None or abs(x_value - last_coords[0]) > 1.0 or abs(y_value - last_coords[1]) > 1.0:
                    if last_coords is not None:
                        movement_changes.append(current_coords)
                last_coords = current_coords

                # Log and display coordinates
                print(f"Y Address: {hex(base_address_xy)} (offset 0): Y={y_value}, X Address: {hex(base_address_xy)} (offset 0): X={x_value}")
                actual_offset_y = (base_address_xy - base_address) & 0xFFFFFFFF
                actual_offset_x = (base_address_xy - base_address) & 0xFFFFFFFF
                print(f"Y Offset: {hex(actual_offset_y)}, X Offset: {hex(actual_offset_x)}")
                with open(log_file, "a") as f:
                    f.write(f"{datetime.datetime.now()} - Y Address: {hex(base_address_xy)}, Y Offset: {hex(actual_offset_y)}, Y={y_value}, X Address: {hex(base_address_xy)}, X Offset: {hex(actual_offset_x)}, X={x_value}\n")
            else:
                # Check if D2R is still in focus
                if win32gui.GetForegroundWindow() != hwnd:
                    print("D2R lost focus, stopping monitoring...")
                    break
                invalid_count += 1
                if invalid_count <= max_invalid_reads:
                    print(f"Invalid coordinates detected: Y={y_value if y_value else 0.0}, X={x_value if x_value else 0.0} (Count: {invalid_count}/{max_invalid_reads})")
                elif invalid_count == max_invalid_reads + 1:
                    print("Too many invalid reads, suppressing further invalid coordinate messages...")
            time.sleep(0.02)  # 50 Hz update rate
    except KeyboardInterrupt:
        print("\nMonitoring stopped by user.")
    print(f"Total valid coordinate reads: {valid_read_count}")
    print(f"Unique movement changes detected: {len(movement_changes)}")
    for i, (x, y) in enumerate(movement_changes, 1):
        print(f"Change {i}: X={x}, Y={y}")
    with open(log_file, "a") as f:
        f.write(f"Stopped monitoring at {datetime.datetime.now()}\n")
        f.write(f"Total valid coordinate reads: {valid_read_count}\n")
        f.write(f"Unique movement changes detected: {len(movement_changes)}\n")
        for i, (x, y) in enumerate(movement_changes, 1):
            f.write(f"Change {i}: X={x}, Y={y}\n")

if __name__ == "__main__":
    try:
        pid, hwnd = find_d2r_process()
        if pid and hwnd:
            base_address = get_base_address(pid)
            print(f"D2R PID: {pid}, Base Address: {hex(base_address)}")
            process_handle = win32api.OpenProcess(0x1F0FFF, False, pid)  # PROCESS_ALL_ACCESS
            if process_handle:
                # Base address for both X and Y
                base_address_xy = 0x134d7fd188  # Confirmed address for both
                print("Testing with float precision...")
                monitor_coordinates(process_handle, base_address, base_address_xy, hwnd)
                win32api.CloseHandle(process_handle)
            else:
                print("Failed to open process handle.")
        else:
            print("D2R not found. Ensure the game is running.")
    except Exception as e:
        print(f"Unexpected error: {str(e)}")