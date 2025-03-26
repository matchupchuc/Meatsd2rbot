import win32process
import win32gui
import win32api
import win32con
import ctypes
import time
import datetime
from ctypes.wintypes import HANDLE, DWORD, LPWSTR, MAX_PATH, RECT, HWND

# Preferred difficulty setting
preferred_difficulty = "nightmare"

# State machine for tracking progress
prev_state = {
    "Character Select": False,
    "Difficulty Select": False,
    "LastClicked": None,
    "CurrentState": "CharacterSelect",
    "StateAddress": None,
    "PlayButtonClicked": False,  # Track if Play button was clicked successfully
    "InGameState": None,  # Store the in-game state after loading
    "NightmareClickAttempts": 0  # Track attempts to click Nightmare before resetting
}
state_count = {"Character Select": 0, "Difficulty Select": 0}
read_failure_count = 0
MAX_FAILURES = 5
MIN_PERSISTENCE_SECONDS = 1.0
TRANSITION_DELAY = 1.0
SEARCH_DELAY = 0.001
POST_CLICK_DELAY = 0.5
RETRY_DELAY = 2.0
MAX_RETRIES = 5
MAX_NIGHTMARE_RETRIES = 3  # Maximum attempts to click Nightmare before resetting
STATE_CHECK_INTERVAL = 2.0
MOUSE_POSITION_TOLERANCE = 2  # Allowable pixel difference for mouse position

# Base resolution for coordinates (reference for scaling) - Used for mouse position checks
BASE_RESOLUTION = (1280, 720)

# Button coordinates - All in screen coordinates
BUTTON_COORDS = {
    "character_select_play": (425, 550),  # Screen coordinates
    "difficulty_normal": (506, 273),      # Screen coordinates
    "difficulty_nightmare": (504, 307),   # Screen coordinates
    "difficulty_hell": (512, 348)         # Screen coordinates
}

# Ranges for 1280x720 (client coordinates) - Used for mouse position checks (if needed)
RANGES_1280x720 = {
    "play": {"x": (328, 528), "y": (420, 620)},
    "normal": {"x": (452, 552), "y": (224, 324)},
    "nightmare": {"x": (475, 575), "y": (257, 357)},
    "hell": {"x": (448, 548), "y": (300, 400)}
}

# Base address (will be determined dynamically)
BASE_ADDRESS = None

# Refined patterns (8 bytes, state value at offset 0)
STATE_ADDRESS_PATTERN_531 = bytes.fromhex("13 02 00 00 00 00 00 00")  # State 531 (Character Select)
STATE_ADDRESS_PATTERN_590 = bytes.fromhex("4E 02 00 00 00 00 00 00")  # State 590 (Difficulty Select)
STATE_ADDRESS_PATTERN_14 = bytes.fromhex("0E 00 00 00 00 00 00 00")  # State 14 (Loading Screen)
STATE_ADDRESS_PATTERN_53 = bytes.fromhex("35 00 00 00 00 00 00 00")  # State 53 (In-Game State)
STATE_ADDRESS_PATTERN_MASK = "xxxx????"  # State value at offset 0
STATE_ADDRESS_OFFSET = 0

# Known in-game states (removed state 14 as it's a loading state)
IN_GAME_STATES = [10, 51, 53, 67, 103, 123, 138, 210, 268, 381, 417, 489]

# Windows API setup
kernel32 = ctypes.WinDLL('kernel32')
user32 = ctypes.WinDLL('user32', use_last_error=True)
dwmapi = ctypes.WinDLL('dwmapi', use_last_error=True)

class MODULEENTRY32W(ctypes.Structure):
    _fields_ = [
        ("dwSize", DWORD),
        ("th32ModuleID", DWORD),
        ("th32ProcessID", DWORD),
        ("GlblcntUsage", DWORD),
        ("ProccntUsage", DWORD),
        ("modBaseAddr", ctypes.c_void_p),
        ("modBaseSize", DWORD),
        ("hModule", HANDLE),
        ("szModule", ctypes.c_wchar * 256),
        ("szExePath", ctypes.c_wchar * 260)
    ]
    _pack_ = 8

class MEMORY_BASIC_INFORMATION(ctypes.Structure):
    _fields_ = [
        ("BaseAddress", ctypes.c_void_p),
        ("AllocationBase", ctypes.c_void_p),
        ("AllocationProtect", DWORD),
        ("PartitionId", ctypes.c_ushort),
        ("RegionSize", ctypes.c_size_t),
        ("State", DWORD),
        ("Protect", DWORD),
        ("Type", DWORD)
    ]

# SendInput structures
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_ABSOLUTE = 0x8000

class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", ctypes.wintypes.LONG),
                ("dy", ctypes.wintypes.LONG),
                ("mouseData", DWORD),
                ("dwFlags", DWORD),
                ("time", DWORD),
                ("dwExtraInfo", ctypes.c_void_p)]

class INPUT(ctypes.Structure):
    _fields_ = [("type", DWORD),
                ("mi", MOUSEINPUT)]
    INPUT_MOUSE = 0

# DWM constants for DwmGetWindowAttribute
DWMWA_EXTENDED_FRAME_BOUNDS = 9

CreateToolhelp32Snapshot = kernel32.CreateToolhelp32Snapshot
CreateToolhelp32Snapshot.argtypes = [DWORD, DWORD]
CreateToolhelp32Snapshot.restype = HANDLE

Module32FirstW = kernel32.Module32FirstW
Module32FirstW.argtypes = [HANDLE, ctypes.POINTER(MODULEENTRY32W)]
Module32FirstW.restype = ctypes.c_bool

Module32NextW = kernel32.Module32NextW
Module32NextW.argtypes = [HANDLE, ctypes.POINTER(MODULEENTRY32W)]
Module32NextW.restype = ctypes.c_bool

CloseHandle = kernel32.CloseHandle
CloseHandle.argtypes = [HANDLE]
CloseHandle.restype = ctypes.c_bool

GetLastError = kernel32.GetLastError
GetLastError.restype = DWORD

ReadProcessMemory = kernel32.ReadProcessMemory
ReadProcessMemory.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_size_t, ctypes.POINTER(ctypes.c_size_t)]
ReadProcessMemory.restype = ctypes.c_bool

VirtualQueryEx = kernel32.VirtualQueryEx
VirtualQueryEx.argtypes = [HANDLE, ctypes.c_void_p, ctypes.POINTER(MEMORY_BASIC_INFORMATION), ctypes.c_size_t]
VirtualQueryEx.restype = ctypes.c_size_t

DwmGetWindowAttribute = dwmapi.DwmGetWindowAttribute
DwmGetWindowAttribute.argtypes = [HWND, DWORD, ctypes.c_void_p, DWORD]
DwmGetWindowAttribute.restype = ctypes.HRESULT

def get_screen_resolution():
    """Get the current screen resolution."""
    return (user32.GetSystemMetrics(0), user32.GetSystemMetrics(1))

def get_window_info(hwnd):
    """Get the D2R window's client area size, position, and border offsets using DWM."""
    client_rect = ctypes.wintypes.RECT()
    user32.GetClientRect(hwnd, ctypes.byref(client_rect))
    client_width = client_rect.right - client_rect.left
    client_height = client_rect.bottom - client_rect.top

    window_rect = ctypes.wintypes.RECT()
    user32.GetWindowRect(hwnd, ctypes.byref(window_rect))

    frame_rect = ctypes.wintypes.RECT()
    result = DwmGetWindowAttribute(hwnd, DWMWA_EXTENDED_FRAME_BOUNDS, ctypes.byref(frame_rect), ctypes.sizeof(frame_rect))
    if result != 0:
        print(f"DwmGetWindowAttribute failed with error: {result}, falling back to GetWindowRect")
        frame_rect = window_rect

    window_x, window_y = frame_rect.left, frame_rect.top

    border_width = (frame_rect.right - frame_rect.left - client_width) // 2
    title_bar_height = frame_rect.bottom - frame_rect.top - client_height - border_width

    border_width = min(border_width, 4)
    title_bar_height = min(title_bar_height, 25)

    return (client_width, client_height), (window_x, window_y), (border_width, title_bar_height)

def get_mouse_position(hwnd):
    screen_pos = win32api.GetCursorPos()
    client_pos = win32gui.ScreenToClient(hwnd, screen_pos)
    return client_pos

def scale_coordinates(x, y, base_res, current_res):
    scale_x = current_res[0] / base_res[0]
    scale_y = current_res[1] / base_res[1]
    return (int(x * scale_x), int(y * scale_y))

def scale_range(range_dict, base_res, current_res):
    scale_x = current_res[0] / base_res[0]
    scale_y = current_res[1] / base_res[1]
    return {
        "x": (int(range_dict["x"][0] * scale_x), int(range_dict["x"][1] * scale_x)),
        "y": (int(range_dict["y"][0] * scale_y), int(range_dict["y"][1] * scale_y))
    }

def send_mouse_click(x, y, screen_res, double_click=False):
    """Simulate a mouse click at absolute screen coordinates, with optional double-click."""
    abs_x = int(x * 65535 / screen_res[0])
    abs_y = int(y * 65535 / screen_res[1])

    time.sleep(0.2)
    mouse_move = INPUT(type=INPUT.INPUT_MOUSE,
                       mi=MOUSEINPUT(dx=abs_x, dy=abs_y, mouseData=0,
                                     dwFlags=MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE,
                                     time=0, dwExtraInfo=None))
    user32.SendInput(1, ctypes.byref(mouse_move), ctypes.sizeof(mouse_move))

    # First click
    mouse_down = INPUT(type=INPUT.INPUT_MOUSE,
                       mi=MOUSEINPUT(dx=0, dy=0, mouseData=0,
                                     dwFlags=MOUSEEVENTF_LEFTDOWN,
                                     time=0, dwExtraInfo=None))
    user32.SendInput(1, ctypes.byref(mouse_down), ctypes.sizeof(mouse_down))

    time.sleep(0.05)

    mouse_up = INPUT(type=INPUT.INPUT_MOUSE,
                     mi=MOUSEINPUT(dx=0, dy=0, mouseData=0,
                                   dwFlags=MOUSEEVENTF_LEFTUP,
                                   time=0, dwExtraInfo=None))
    user32.SendInput(1, ctypes.byref(mouse_up), ctypes.sizeof(mouse_up))

    if double_click:
        # Short delay before second click
        time.sleep(0.1)
        # Second click
        user32.SendInput(1, ctypes.byref(mouse_down), ctypes.sizeof(mouse_down))
        time.sleep(0.05)
        user32.SendInput(1, ctypes.byref(mouse_up), ctypes.sizeof(mouse_up))

    # Add a small delay and re-check mouse position with tolerance
    time.sleep(0.1)
    final_pos = win32api.GetCursorPos()
    if abs(final_pos[0] - x) > MOUSE_POSITION_TOLERANCE or abs(final_pos[1] - y) > MOUSE_POSITION_TOLERANCE:
        print(f"Warning: Mouse position after click ({final_pos}) does not match intended position ({x}, {y}) within tolerance of {MOUSE_POSITION_TOLERANCE} pixels")

    time.sleep(POST_CLICK_DELAY)

def click_button(hwnd, x, y, current_res, window_pos, border_offsets, screen_res, use_current_pos=False, is_play_button=False):
    """Click at exact coordinates (all in screen coordinates)."""
    # Log mouse position and window focus before clicking
    screen_pos = win32api.GetCursorPos()
    focused_window = user32.GetForegroundWindow()
    print(f"Before click: Mouse position (screen): {screen_pos}, Focused window: {focused_window == hwnd}")

    click_x, click_y = x, y
    print(f"Simulating click at exact screen coordinates ({click_x}, {click_y})")
    # Single click for Play button
    send_mouse_click(click_x, click_y, screen_res, double_click=False)

    # Log mouse position and window focus after clicking
    screen_pos = win32api.GetCursorPos()
    focused_window = user32.GetForegroundWindow()
    print(f"After click: Mouse position (screen): {screen_pos}, Focused window: {focused_window == hwnd}")

def ensure_d2r_active(hwnd, max_attempts=5):
    for attempt in range(max_attempts):
        if user32.GetForegroundWindow() == hwnd:
            print("D2R window is active. Proceeding...")
            return True
        print(f"D2R window is not active. Attempt {attempt + 1}/{max_attempts} to reactivate...")
        user32.ShowWindow(hwnd, 5)
        success = user32.SetForegroundWindow(hwnd)
        if not success:
            error = ctypes.get_last_error()
            print(f"Error: {error}, 'SetForegroundWindow', 'No error message is available'")
        time.sleep(0.5)
        if user32.GetForegroundWindow() == hwnd:
            print("D2R window regained focus. Proceeding...")
            return True
    print("Failed to activate D2R window after max attempts.")
    return False

def click_and_wait_for_state(hwnd, process_handle, state_address, expected_state, x, y, current_res, window_pos, border_offsets, screen_res, retries=MAX_RETRIES, is_play_button=False, expect_loading=False):
    global prev_state
    for attempt in range(retries):
        if not ensure_d2r_active(hwnd):
            print("Focus lost before click. Aborting attempt.")
            return False, state_address

        print(f"Attempt {attempt + 1}/{retries}: Clicking at exact screen coordinates ({x}, {y})")
        click_button(hwnd, x, y, current_res, window_pos, border_offsets, screen_res, is_play_button=is_play_button)
        time.sleep(TRANSITION_DELAY)

        # Check the current state once
        current_state = read_int(process_handle, state_address)
        if current_state is None:
            print("Failed to read state after click.")
        else:
            print(f"State after click: {current_state}")
            if expect_loading and current_state == 14:  # Loading screen detected
                print("Loading screen (state 14) detected. Monitoring address for state change and re-searching broadly...")
                # Monitor the current address for a change and perform broad search for in-game states
                max_attempts = 10  # Maximum attempts to prevent infinite loop
                attempt_count = 0
                while attempt_count < max_attempts:
                    attempt_count += 1
                    # First, check if the state at the current address has changed
                    current_state = read_int(process_handle, state_address)
                    if current_state is not None:
                        print(f"State at known address (attempt {attempt_count}): {current_state}")
                        if current_state in IN_GAME_STATES:  # State 14 is no longer in IN_GAME_STATES
                            print(f"In-game state {current_state} detected at known address.")
                            prev_state["InGameState"] = current_state
                            return True, state_address
                        elif current_state != 14:
                            print(f"Unexpected state {current_state} detected at known address. Re-searching...")
                            break

                    # Perform a broad search for in-game states
                    state_pattern = STATE_ADDRESS_PATTERN_53  # Start with state 53 pattern
                    new_state_address = find_state_address(process_handle, BASE_ADDRESS, module_size, state_pattern=state_pattern)
                    if new_state_address is not None:
                        state_address = new_state_address
                        prev_state["StateAddress"] = state_address
                        print(f"New state address after loading screen (attempt {attempt_count}): {hex(state_address)}")
                        current_state = read_int(process_handle, state_address)
                        if current_state is not None:
                            print(f"Updated state after re-search (attempt {attempt_count}): {current_state}")
                            if current_state in IN_GAME_STATES:
                                print(f"In-game state {current_state} detected after re-search.")
                                prev_state["InGameState"] = current_state
                                return True, state_address
                    time.sleep(STATE_CHECK_INTERVAL)
                print("Failed to detect in-game state after loading screen within max attempts.")
                return False, state_address

            if current_state == expected_state:
                print(f"State transitioned to {expected_state} successfully.")
                if is_play_button:
                    prev_state["PlayButtonClicked"] = True  # Mark Play button as successfully clicked
                return True, state_address

        # Re-search for the state address if the transition didn't occur
        state_pattern = STATE_ADDRESS_PATTERN_590 if expected_state == 590 else STATE_ADDRESS_PATTERN_14 if expect_loading else STATE_ADDRESS_PATTERN_531
        print(f"Expected state {expected_state} not found, re-searching for state...")
        new_state_address = find_state_address(process_handle, BASE_ADDRESS, module_size, state_pattern=state_pattern)
        if new_state_address is not None:
            state_address = new_state_address
            prev_state["StateAddress"] = state_address
            print(f"New state address: {hex(state_address)}")
            new_state = read_int(process_handle, state_address)
            if new_state == expected_state:
                print(f"State transitioned to {expected_state} successfully after re-search.")
                if is_play_button:
                    prev_state["PlayButtonClicked"] = True
                return True, state_address
            elif expect_loading and new_state in IN_GAME_STATES:
                print(f"In-game state {new_state} detected after re-search.")
                prev_state["InGameState"] = new_state
                return True, state_address

        if attempt < retries - 1:
            time.sleep(RETRY_DELAY)

    print(f"Failed to transition to state {expected_state} after {retries} attempts.")
    return False, state_address

def get_base_address(pid):
    TH32CS_SNAPMODULE = 0x00000008
    TH32CS_SNAPMODULE32 = 0x00000010
    print(f"Creating module snapshot for PID {pid}...")
    snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid)
    if snapshot == -1:
        error_code = GetLastError()
        raise Exception(f"CreateToolhelp32Snapshot failed with error code: {error_code}")

    print(f"Snapshot handle: {snapshot}")
    try:
        module_entry = MODULEENTRY32W()
        module_entry.dwSize = ctypes.sizeof(MODULEENTRY32W)
        print(f"Structure size: {ctypes.sizeof(MODULEENTRY32W)} bytes")

        print("Calling Module32FirstW...")
        if not Module32FirstW(snapshot, ctypes.byref(module_entry)):
            error_code = GetLastError()
            if error_code == 24:
                raise Exception(f"Module32FirstW failed with ERROR_INSUFFICIENT_BUFFER (code 24).")
            else:
                raise Exception(f"Module32FirstW failed with error code: {error_code}")

        while True:
            module_name = module_entry.szModule.lower()
            print(f"Found module: {module_name}, Base address: {hex(module_entry.modBaseAddr)}")
            if "d2r.exe" in module_name or "diablo ii resurrected" in module_name:
                base_addr = module_entry.modBaseAddr
                module_size = module_entry.modBaseSize
                MAX_MODULE_SIZE = 0x10000000
                if module_size > MAX_MODULE_SIZE:
                    print(f"Reported module size {hex(module_size)} seems too large. Capping at {hex(MAX_MODULE_SIZE)}.")
                    module_size = MAX_MODULE_SIZE
                print(f"Found D2R.exe module: {module_name}, Base address: {hex(base_addr)}, Size: {hex(module_size)}")
                return base_addr, module_size

            print("Calling Module32NextW...")
            if not Module32NextW(snapshot, ctypes.byref(module_entry)):
                error_code = GetLastError()
                if error_code == 18:
                    break
                raise Exception(f"Module32NextW failed with error code: {error_code}")

        raise Exception("D2R.exe module not found in process modules.")
    finally:
        print("Closing snapshot handle...")
        CloseHandle(snapshot)

def open_process():
    global read_failure_count, BASE_ADDRESS, module_size
    print("Attempting to find D2R window...")
    hwnd = win32gui.FindWindow(None, "Diablo II: Resurrected")
    if not hwnd:
        raise Exception("D2R window not found. Ensure the game is running.")

    print(f"Found window handle: {hwnd}")
    thread_id, process_id = win32process.GetWindowThreadProcessId(hwnd)
    print(f"Process ID: {process_id}")
    PROCESS_ALL_ACCESS = 0x1F0FFF
    PROCESS_QUERY_INFORMATION = 0x0400
    PROCESS_VM_READ = 0x0010
    process_handle = ctypes.windll.kernel32.OpenProcess(
        PROCESS_ALL_ACCESS | PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, False, process_id
    )
    if not process_handle:
        error_code = GetLastError()
        raise Exception(f"Failed to open process. Error code: {error_code}")
    print(f"Successfully opened process handle: {process_handle}")

    BASE_ADDRESS, module_size = get_base_address(process_id)
    if BASE_ADDRESS is None:
        raise Exception("Could not determine base address of D2R.exe.")
    print(f"Base address of D2R.exe: {hex(BASE_ADDRESS)}")
    print(f"Module size: {hex(module_size)}")

    read_failure_count = 0
    return hwnd, process_handle, module_size

def read_memory(process_handle, address, size):
    try:
        buffer = ctypes.create_string_buffer(size)
        bytes_read = ctypes.c_size_t()
        address_ptr = ctypes.c_void_p(address & 0xFFFFFFFFFFFFFFFF)
        success = ReadProcessMemory(
            process_handle,
            address_ptr,
            buffer,
            size,
            ctypes.byref(bytes_read)
        )
        if not success or bytes_read.value != size:
            error_code = GetLastError()
            print(f"Failed to read memory at {hex(address)}. Success: {success}, Bytes read: {bytes_read.value}, Error code: {error_code}")
            return None
        return buffer.raw
    except Exception as e:
        print(f"Error reading memory at {hex(address)}: {e}")
        return None

def read_int(process_handle, address):
    try:
        buffer = ctypes.c_int()
        bytes_read = ctypes.c_size_t()
        address_ptr = ctypes.c_void_p(address & 0xFFFFFFFFFFFFFFFF)
        success = ReadProcessMemory(
            process_handle,
            address_ptr,
            ctypes.byref(buffer),
            ctypes.sizeof(buffer),
            ctypes.byref(bytes_read)
        )
        if not success or bytes_read.value != ctypes.sizeof(buffer):
            error_code = GetLastError()
            print(f"Failed to read memory at {hex(address)}. Success: {success}, Bytes read: {bytes_read.value}, Error code: {error_code}")
            return None
        print(f"Successfully read integer: {buffer.value} from {hex(address)}")
        return buffer.value
    except Exception as e:
        print(f"Error reading memory at {hex(address)}: {e}")
        return None
    finally:
        time.sleep(SEARCH_DELAY)

def find_state_address(process_handle, base_address, module_size, state_pattern=STATE_ADDRESS_PATTERN_531):
    MEM_COMMIT = 0x1000
    PAGE_READWRITE = 0x04
    address = 0  # Start from the beginning of the address space
    mbi = MEMORY_BASIC_INFORMATION()
    pattern_length = len(state_pattern)

    print("Enumerating memory regions from 0x0 to maximum addressable space...")
    # On 64-bit systems, the user-mode address space is typically up to 0x7FFFFFFFFFFF
    MAX_ADDRESS = 0x7FFFFFFFFFFF  # Maximum addressable user-mode space on 64-bit Windows
    while address < MAX_ADDRESS:
        size = VirtualQueryEx(process_handle, ctypes.c_void_p(address), ctypes.byref(mbi), ctypes.sizeof(mbi))
        if size == 0:
            error_code = GetLastError()
            if error_code == 998:  # ERROR_NOACCESS
                address += 0x1000  # Skip to the next page
                continue
            else:
                print(f"VirtualQueryEx failed at address {hex(address)} with error code: {error_code}")
                break

        if mbi.State == MEM_COMMIT and mbi.Protect & PAGE_READWRITE:
            start_address = mbi.BaseAddress
            end_address = mbi.BaseAddress + mbi.RegionSize
            print(f"Searching in region {hex(start_address)} to {hex(end_address)} (Size: {hex(mbi.RegionSize)})")

            chunk_size = 0x8000
            for start in range(start_address, end_address, chunk_size):
                end = min(start + chunk_size, end_address)
                memory = read_memory(process_handle, start, end - start)
                if memory is None:
                    continue

                for i in range(len(memory) - pattern_length + 1):
                    match = True
                    for j in range(pattern_length):
                        if STATE_ADDRESS_PATTERN_MASK[j] == "x" and memory[i + j] != state_pattern[j]:
                            match = False
                            break
                    if match:
                        state_address = start + i + STATE_ADDRESS_OFFSET
                        print(f"Found state address at {hex(state_address)}")
                        state_value = read_int(process_handle, state_address)
                        if state_value is not None:
                            print(f"State ID found: {state_value} at address {hex(state_address)}")
                        else:
                            print(f"Failed to read state value at {hex(state_address)}")
                        return state_address
        address += mbi.RegionSize if mbi.RegionSize > 0 else 0x1000  # Ensure we increment the address

    print("Pattern not found after scanning all accessible memory regions.")
    return None

def check_game_state(hwnd, process_handle, module_size):
    global prev_state, state_count, read_failure_count

    screen_res = get_screen_resolution()
    current_res, window_pos, border_offsets = get_window_info(hwnd)
    print(f"Screen resolution: {screen_res}")
    print(f"D2R client resolution: {current_res}")
    print(f"D2R window position: {window_pos}")
    print(f"Border offsets (width, title bar height): {border_offsets}")

    play_range = scale_range(RANGES_1280x720["play"], BASE_RESOLUTION, current_res)
    normal_range = scale_range(RANGES_1280x720["normal"], BASE_RESOLUTION, current_res)
    nightmare_range = scale_range(RANGES_1280x720["nightmare"], BASE_RESOLUTION, current_res)
    hell_range = scale_range(RANGES_1280x720["hell"], BASE_RESOLUTION, current_res)
    print(f"Scaled Play Range: {play_range}")
    print(f"Scaled Normal Range: {normal_range}")
    print(f"Scaled Nightmare Range: {nightmare_range}")
    print(f"Scaled Hell Range: {hell_range}")

    if prev_state["StateAddress"] is None or read_failure_count >= MAX_FAILURES:
        prev_state["StateAddress"] = find_state_address(process_handle, BASE_ADDRESS, module_size)
        if prev_state["StateAddress"] is None:
            print("State address not found. Automation disabled until a valid address is set.")
            return {
                "Character Select": False,
                "Difficulty Select": False,
                "Hovered": False,
                "Additional": {},
                "InGameState": None
            }

    state_address = prev_state["StateAddress"]
    print(f"Using state address: {hex(state_address)}")

    address_values = {}
    all_failed = True
    value = read_int(process_handle, state_address)
    if value is not None:
        all_failed = False
        address_values[hex(state_address).lower()] = value
    else:
        read_failure_count += 1
        print(f"Consecutive read failures: {read_failure_count}")
        if read_failure_count >= MAX_FAILURES:
            print("Too many read failures, attempting to find new state address...")
            if prev_state["CurrentState"] == "InGame":
                print("Re-searching for in-game state (state 14 or other in-game states)...")
                new_state_address = find_state_address(process_handle, BASE_ADDRESS, module_size, state_pattern=STATE_ADDRESS_PATTERN_14)
                if new_state_address is None:
                    print("Could not find state 14, trying Character Select state (531)...")
                    new_state_address = find_state_address(process_handle, BASE_ADDRESS, module_size, state_pattern=STATE_ADDRESS_PATTERN_531)
            else:
                new_state_address = find_state_address(process_handle, BASE_ADDRESS, module_size)

            prev_state["StateAddress"] = new_state_address
            read_failure_count = 0
            if prev_state["StateAddress"] is None:
                print("Failed to recover state address. Automation disabled.")
                return {
                    "Character Select": False,
                    "Difficulty Select": False,
                    "Hovered": False,
                    "Additional": {},
                    "InGameState": None
                }
            value = read_int(process_handle, new_state_address)
            if value is not None:
                address_values[hex(new_state_address).lower()] = value

    if not all_failed:
        read_failure_count = 0
    print(f"Address values: {address_values}")

    # Check for in-game state first if we're in InGame state
    in_game_detected = False
    if prev_state["CurrentState"] == "InGame":
        if value in IN_GAME_STATES:
            in_game_detected = True
            print(f"In-game state detected: {value}")
            prev_state["InGameState"] = value
        elif value == 14:  # If still in loading screen, keep re-searching
            print("Still in loading screen (state 14), monitoring address for state change and re-searching broadly...")
            max_attempts = 10  # Maximum attempts to prevent infinite loop
            attempt_count = 0
            while attempt_count < max_attempts:
                attempt_count += 1
                # First, check if the state at the current address has changed
                current_state = read_int(process_handle, state_address)
                if current_state is not None:
                    print(f"State at known address (attempt {attempt_count}): {current_state}")
                    if current_state in IN_GAME_STATES:
                        in_game_detected = True
                        print(f"In-game state detected at known address: {current_state}")
                        prev_state["InGameState"] = current_state
                        value = current_state
                        break
                    elif current_state != 14:
                        print(f"Unexpected state {current_state} detected at known address. Re-searching...")
                        break

                # Perform a broad search for in-game states
                state_pattern = STATE_ADDRESS_PATTERN_53  # Start with state 53 pattern
                new_state_address = find_state_address(process_handle, BASE_ADDRESS, module_size, state_pattern=state_pattern)
                if new_state_address is not None:
                    state_address = new_state_address
                    prev_state["StateAddress"] = state_address
                    print(f"New state address during in-game check (attempt {attempt_count}): {hex(state_address)}")
                    value = read_int(process_handle, state_address)
                    if value is not None:
                        address_values[hex(state_address).lower()] = value
                        print(f"Updated state after re-search (attempt {attempt_count}): {value}")
                        if value in IN_GAME_STATES:
                            in_game_detected = True
                            print(f"In-game state detected after re-search: {value}")
                            prev_state["InGameState"] = value
                            break
                time.sleep(STATE_CHECK_INTERVAL)
            if not in_game_detected:
                print("Failed to detect in-game state after loading screen within max attempts.")
        else:
            print("In-game state not detected at current address, re-searching for state 14 or in-game states...")
            state_pattern = STATE_ADDRESS_PATTERN_14
            new_state_address = find_state_address(process_handle, BASE_ADDRESS, module_size, state_pattern=state_pattern)
            if new_state_address is not None:
                state_address = new_state_address
                prev_state["StateAddress"] = state_address
                value = read_int(process_handle, state_address)
                if value in IN_GAME_STATES:
                    in_game_detected = True
                    print(f"In-game state detected after re-search: {value}")
                    prev_state["InGameState"] = value
                    address_values[hex(state_address).lower()] = value
                elif value != 14:
                    print(f"Unexpected state {value} detected after re-search. Continuing to monitor...")

    character_select = value == 531 if value is not None else prev_state["Character Select"]
    difficulty_select = value == 590 if value is not None else prev_state["Difficulty Select"]
    print(f"Character Select detected: {character_select} (State ID: {value})")
    print(f"Difficulty Select detected: {difficulty_select} (State ID: {value})")

    if character_select:
        state_count["Character Select"] += 1
        print(f"Character Select count: {state_count['Character Select']}")
    else:
        state_count["Character Select"] = 0
    if difficulty_select:
        state_count["Difficulty Select"] += 1
        print(f"Difficulty Select count: {state_count['Difficulty Select']}")
    else:
        state_count["Difficulty Select"] = 0

    mouse_x, mouse_y = get_mouse_position(hwnd)
    mouse_x = min(max(mouse_x, 0), current_res[0])
    mouse_y = min(max(mouse_y, 0), current_res[1])
    print(f"Mouse position (client): ({mouse_x}, {mouse_y})")

    if prev_state["StateAddress"] is not None:
        if prev_state["CurrentState"] == "CharacterSelect":
            if character_select and state_count["Character Select"] * 0.2 >= MIN_PERSISTENCE_SECONDS and prev_state["LastClicked"] != "character_select_play" and not prev_state["PlayButtonClicked"]:
                success, new_state_address = click_and_wait_for_state(hwnd, process_handle, state_address, 590, BUTTON_COORDS["character_select_play"][0], BUTTON_COORDS["character_select_play"][1], current_res, window_pos, border_offsets, screen_res, is_play_button=True)
                state_address = new_state_address  # Update state address
                prev_state["StateAddress"] = state_address
                if success:
                    prev_state["LastClicked"] = "character_select_play"
                    prev_state["CurrentState"] = "DifficultySelect"
                    prev_state["NightmareClickAttempts"] = 0  # Reset Nightmare click attempts
                    print(f"Clicked 'Play' at ({BUTTON_COORDS['character_select_play'][0]}, {BUTTON_COORDS['character_select_play'][1]}), moving to DifficultySelect state")
                else:
                    print("Failed to click 'Play' and transition state. Aborting this cycle.")

        elif prev_state["CurrentState"] == "DifficultySelect":
            if character_select and state_count["Character Select"] * 0.2 >= MIN_PERSISTENCE_SECONDS:
                print("Returned to Character Select screen unexpectedly, resetting state")
                prev_state["CurrentState"] = "CharacterSelect"
                prev_state["LastClicked"] = None
                prev_state["PlayButtonClicked"] = False
                prev_state["NightmareClickAttempts"] = 0
            elif in_game_detected:
                print("In-game state confirmed, moving to InGame state")
                prev_state["CurrentState"] = "InGame"
                prev_state["PlayButtonClicked"] = False
                prev_state["NightmareClickAttempts"] = 0
            elif difficulty_select:
                difficulty_key = f"difficulty_{preferred_difficulty}"
                if prev_state["LastClicked"] != difficulty_key:
                    prev_state["NightmareClickAttempts"] += 1
                    if prev_state["NightmareClickAttempts"] > MAX_NIGHTMARE_RETRIES:
                        print(f"Failed to transition to loading screen after {MAX_NIGHTMARE_RETRIES} attempts. Resetting to CharacterSelect state.")
                        prev_state["CurrentState"] = "CharacterSelect"
                        prev_state["LastClicked"] = None
                        prev_state["PlayButtonClicked"] = False
                        prev_state["NightmareClickAttempts"] = 0
                    else:
                        # Expect loading screen (state 14) and then an in-game state
                        success, new_state_address = click_and_wait_for_state(hwnd, process_handle, state_address, 14, BUTTON_COORDS[difficulty_key][0], BUTTON_COORDS[difficulty_key][1], current_res, window_pos, border_offsets, screen_res, retries=1, is_play_button=False, expect_loading=True)
                        state_address = new_state_address  # Update state address
                        prev_state["StateAddress"] = state_address
                        if success:
                            prev_state["LastClicked"] = difficulty_key
                            prev_state["CurrentState"] = "InGame"
                            prev_state["PlayButtonClicked"] = False  # Reset for next cycle
                            prev_state["NightmareClickAttempts"] = 0
                            print(f"Clicked '{preferred_difficulty.capitalize()}' at ({BUTTON_COORDS[difficulty_key][0]}, {BUTTON_COORDS[difficulty_key][1]}), moving to InGame state")
                        else:
                            print("Failed to click difficulty button. Aborting this cycle.")
            elif not character_select and not difficulty_select:
                prev_state["CurrentState"] = "InGame"
                prev_state["PlayButtonClicked"] = False  # Reset for next cycle
                prev_state["NightmareClickAttempts"] = 0
                print("Neither Character Select nor Difficulty Select detected, assuming InGame state")

        elif prev_state["CurrentState"] == "InGame":
            if character_select and state_count["Character Select"] * 0.2 >= MIN_PERSISTENCE_SECONDS:
                prev_state["CurrentState"] = "CharacterSelect"
                prev_state["LastClicked"] = None
                prev_state["PlayButtonClicked"] = False  # Reset for next cycle
                prev_state["InGameState"] = None
                prev_state["NightmareClickAttempts"] = 0
                print("Returned to Character Select screen, resetting state")

    prev_state["Character Select"] = character_select
    prev_state["Difficulty Select"] = difficulty_select

    current_state = {
        "Character Select": character_select,
        "Difficulty Select": difficulty_select,
        "Hovered": False,
        "Mouse Position": (mouse_x, mouse_y),
        "Additional": address_values,
        "InGameState": prev_state["InGameState"]
    }
    print(f"Game State: {current_state}")
    print(f"Previous State: {prev_state}")
    return current_state

def main():
    global process_handle, module_size
    try:
        hwnd, process_handle, module_size = open_process()
        ensure_d2r_active(hwnd)
        log_file = open("coordinates_log.txt", "a")

        print("Starting OffsetFinder... DO NOT interrupt with Ctrl+C; let the script complete.")
        print(f"Bot will automatically click 'Play' in Character Select and '{preferred_difficulty.capitalize()}' in Difficulty Select.")
        print("Ensure a character is selected in the Character Select menu before running.")
        print("Searching for state address using pattern with fallback to manual verification.")
        start_time = time.time()
        max_runtime = 300
        while time.time() - start_time < max_runtime:
            try:
                hwnd_check = win32gui.FindWindow(None, "Diablo II: Resurrected")
                if not hwnd_check:
                    print("D2R window not found, attempting to reopen...")
                    ctypes.windll.kernel32.CloseHandle(process_handle)
                    hwnd, process_handle, module_size = open_process()
                    ensure_d2r_active(hwnd)
                    continue

                exit_code = ctypes.c_int()
                if not ctypes.windll.kernel32.GetExitCodeProcess(process_handle, ctypes.byref(exit_code)):
                    print("Process handle invalid, reopening...")
                    ctypes.windll.kernel32.CloseHandle(process_handle)
                    hwnd, process_handle, module_size = open_process()
                    ensure_d2r_active(hwnd)

                if not ensure_d2r_active(hwnd):
                    print("OffsetFinder stopped due to persistent focus issues.")
                    break

                game_state = check_game_state(hwnd, process_handle, module_size)
                timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                log_file.write(f"{timestamp} - Game State: {game_state}\n")
                log_file.flush()
            except KeyboardInterrupt:
                print("Ctrl+C detected. Logging state and exiting...")
                break

            time.sleep(0.5)

        else:
            print("Max runtime exceeded. Stopping...")
    except Exception as e:
        print(f"Error: {e}")
        with open("coordinates_log.txt", "a") as log_file:
            log_file.write(f"{datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')} - Error: {e}\n")
    finally:
        if 'process_handle' in globals() and process_handle:
            ctypes.windll.kernel32.CloseHandle(process_handle)
        if 'log_file' in locals() and not log_file.closed:
            log_file.close()
        print("OffsetFinder stopped.")

if __name__ == "__main__":
    main()