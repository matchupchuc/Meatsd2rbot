import win32process
import win32gui
import win32api
import ctypes

def find_d2r_process():
    """Find the PID of the D2R process."""
    hwnd = win32gui.FindWindow(None, "Diablo II: Resurrected")
    if hwnd:
        print(f"Found D2R window with title: {win32gui.GetWindowText(hwnd)}")
        tid, pid = win32process.GetWindowThreadProcessId(hwnd)
        return pid
    else:
        print("D2R window not found. Listing all windows...")
        def enum_windows():
            def callback(hwnd, extra):
                title = win32gui.GetWindowText(hwnd)
                if title:
                    print(f"Window handle: {hwnd}, Title: {title}")
        win32gui.EnumWindows(callback, None)
        return None

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

def test_addresses_for_coordinates(process_handle, base_address, addresses, min_coord, max_coord):
    """Test the provided addresses for any coordinate pair within the specified range as 4-byte floats."""
    print(f"Testing {len(addresses)} addresses for coordinates in range {min_coord} to {max_coord}...")
    found = False
    for idx, addr in enumerate(addresses):
        # Check nearby offsets for X and Y
        for offset in range(-16, 17, 4):  # Check -16 to +16 bytes, stepping by 4
            x_addr = addr + offset
            x_value = read_memory_float(process_handle, x_addr)
            if x_value is not None and min_coord <= x_value <= max_coord:
                y_value = read_memory_float(process_handle, x_addr + 4)  # Check next 4 bytes for Y
                if y_value is not None and min_coord <= y_value <= max_coord:
                    print(f"Found coordinates at address {hex(x_addr)} (ID {idx}, offset {offset}): X={x_value}, Y={y_value}")
                    actual_offset = x_addr - base_address if x_addr >= base_address else base_address - x_addr
                    print(f"Offset from base address: {hex(actual_offset)}")
                    with open("C:/Users/hayes/Documents/GitHub/MeatsD2RBot/offsets.txt", "a") as f:
                        f.write(f"{hex(actual_offset)}\n")
                    found = True
    if not found:
        print("No coordinates found within the specified range.")
    return None, None

if __name__ == "__main__":
    try:
        pid = find_d2r_process()
        if pid:
            base_address = get_base_address(pid)
            print(f"D2R PID: {pid}, Base Address: {hex(base_address)}")
            process_handle = win32api.OpenProcess(0x1F0FFF, False, pid)  # PROCESS_ALL_ACCESS
            if process_handle:
                # Update with the full 2824 addresses from your Cheat Engine table
                addresses = [
                    0x27DC87BF02C, 0x27DC87BF030, 0x27DC87BF034, 0x27DC87BF038, 0x27DC87BF06C, 0x27DC87BF070,
                    0x27DC87BF074, 0x27DC87BF078, 0x27DC88309BC, 0x27DC88309C0, 0x27DC88309C4, 0x27DC88309C8,
                    0x27DC890255C, 0x27DC8902560, 0x27DC8A9FE9C, 0x27DC8A9FEA0, 0x27DC8A9FEA4, 0x27DC8A9FEA8,
                    0x27DC8A9FEDC, 0x27DC8A9FEE0, 0x27DC8A9FEE4, 0x27DC8A9FEE8, 0x27DC8A9FF5C, 0x27DC8A9FF60,
                    0x27DC8A9FF64, 0x27DC8A9FF68, 0x27DC8A9FF6C, 0x27DC8A9FF70, 0x27DC8A9FF74, 0x27DC8A9FF78,
                    0x27DC8A9FF7C, 0x27DC8A9FF80, 0x27DC8A9FF84, 0x27DC8AA000C, 0x27DC8AA0010, 0x27DC8AA0014,
                    0x27DC8AA0018, 0x27DC8AA004C, 0x27DC8AA0050, 0x27DC8AA0054, 0x27DC8AA0058, 0x27DC8AA00CC,
                    0x27DC8AA00D0, 0x27DC8AA00D4, 0x27DC8AA00D8, 0x27DC8AA00DC, 0x27DC8AA00E0, 0x27DC8AA00E4,
                    0x27DC8AA00E8, 0x27DC8AA00EC, 0x27DC8AA00F0, 0x27DC8AA00F4, 0x27DC8E80804, 0x27DC8E80808,
                    0x27DC8E8080C, 0x27DC8E80834, 0x27DC8E80838, 0x27DC8E8083C, 0x27DC8E80864, 0x27DC8E80868,
                    0x27DC8E8086C, 0x27DC8E808C4, 0x27DC8E808C8, 0x27DC8E808CC, 0x27DC8E80954, 0x27DC8E80958,
                    0x27DC8E8095C, 0x27DC8E80984, 0x27DC8E80988, 0x27DC8E8098C, 0x27DC8E809E4, 0x27DC8E809E8,
                    0x27DC8E809EC, 0x27DC8E80A14, 0x27DC8E80A18, 0x27DC8E80A1C, 0x27DC8E80A74, 0x27DC8E80A78,
                    0x27DC8E80A7C, 0x27DC8E80B64, 0x27DC8E80B68, 0x27DC8E80B6C, 0x27DC8E80B94, 0x27DC8E80B98,
                    0x27DC8E80B9C, 0x27DC8E80BC4, 0x27DC8E80BC8, 0x27DC8E80BCC, 0x27DC8E80BF4, 0x27DC8E80BF8,
                    0x27DC8E80BFC, 0x27DC8E80C54, 0x27DC8E80C58, 0x27DC8E80C5C, 0x27DC8E80CE4, 0x27DC8E80CE8,
                    0x27DC8E80CEC, 0x27DC8F7B9EC, 0x27DC8F7B9F0, 0x27DCA780620, 0x27DCA780624, 0x27DCA780630,
                    0x27DCA780634, 0x27DCA78063C, 0x27DCA780640, 0x27DCA780644, 0x27DCA780648, 0x27DCA78064C,
                    0x27DCA780650, 0x27DCA780654, 0x27DCA780658, 0x27DCA78065C, 0x27DCA780660, 0x27DCA780664,
                    0x27DCA780668, 0x27DCA78066C, 0x27DCA780670, 0x27DCA780674, 0x27DCA780678, 0x27DCA78067C,
                    0x27DCA780680, 0x27DCA780684, 0x27DCA780688, 0x27DCA78068C, 0x27DCA780690, 0x27DCA780694,
                    0x27DCA780698, 0x27DCA7806C0, 0x27DCA7806C4, 0x27DCA7806C8, 0x27DCA7806D0, 0x27DCA7806D4,
                    0x27DCA7806D8, 0x27DCA7806E0, 0x27DCA7806E4, 0x27DCA7806E8, 0x27DCA7806F0, 0x27DCA7806F4,
                    0x27DCA7806F8, 0x27DCA780720, 0x27DCA780724, 0x27DCA780730, 0x27DCA780734, 0x27DCA78073C,
                    0x27DCA780740, 0x27DCA780744, 0x27DCA780748, 0x27DCA78074C, 0x27DCA780750, 0x27DCA780754,
                    0x27DCA780758, 0x27DCA78075C, 0x27DCA780760, 0x27DCA780764, 0x27DCA780768, 0x27DCA78076C,
                    0x27DCA780770, 0x27DCA780774, 0x27DCA780778, 0x27DCA78077C, 0x27DCA780780, 0x27DCA780784,
                    0x27DCA780788, 0x27DCA780790, 0x27DCA780794, 0x27DCA780798, 0x27DD0D00620, 0x27DD0D00624,
                    0x27DD0D00630, 0x27DD0D00634, 0x27DD0D0063C, 0x27DD0D00640, 0x27DD0D00644, 0x27DD0D0064C,
                    0x27DD0D00650, 0x27DD0D00654, 0x27DD0D0065C, 0x27DD0D00660, 0x27DD0D00664, 0x27DD0D0066C,
                    0x27DD0D00670, 0x27DD0D00674, 0x27DD0D0067C, 0x27DD0D00680, 0x27DD0D00684, 0x27DD0D00688,
                    0x27DD0D0068C, 0x27DD0D00690, 0x27DD0D00694, 0x27DD0D00698, 0x27DD0D006C0, 0x27DD0D006C4,
                    0x27DD0D006C8, 0x27DD0D006D0, 0x27DD0D006D4, 0x27DD0D006D8, 0x27DD0D006E0, 0x27DD0D006E4,
                    0x27DD0D006E8, 0x27DD0D006F0, 0x27DD0D006F4, 0x27DD0D006F8, 0x27DD0D006FC, 0x27DD0D0071C,
                    0x27DD0D00720, 0x27DD0D00724, 0x27DD0D00728, 0x27DD0D0072C, 0x27DD0D00730, 0x27DD0D00734,
                    0x27DD0D00738, 0x27DD0D0073C, 0x27DD0D00740, 0x27DD0D00744, 0x27DD0D00748, 0x27DD0D0074C,
                    0x27DD0D00750, 0x27DD0D00754, 0x27DD0D00758, 0x27DD0D0075C, 0x27DD0D00760, 0x27DD0D00764,
                    0x27DD0D00768, 0x27DD0D0076C, 0x27DD0D00770, 0x27DD0D00774, 0x27DD0D00778, 0x27DD0D0077C,
                    0x27DD0D00780, 0x27F36C56690, 0x27F36C566C8, 0x27F36C56700, 0x27F36C56738, 0x27F36C56770,
                    0x27F36C567A8, 0x27F36C567E0, 0x27F36C56818, 0x27F36C56850, 0x27F36C56888, 0x27F36C568C0,
                    0x27F36C568F8, 0x27F36C56930, 0x27F36C56968, 0x27F36C569A0, 0x27F36C569D8, 0x27F36C56A10,
                    0x27F36C56A48, 0x27F36C56A80, 0x27F36C56AB8, 0x27F36C56AF0, 0x27F36C56B28, 0x27F36C56B60,
                    0x27F36C56B98, 0x27F36C56BD0, 0x27F36C56C08, 0x27F36C56C40, 0x27F36C56C78, 0x27F36C56CB0,
                    0x27F36C56CE8, 0x27F36C56D20, 0x27F36C56D58, 0x27F36C56D90, 0x27F36C56DC8, 0x27F36C56E00,
                    0x27F36C56E38, 0x27F36C56E70, 0x27F36C56EA8, 0x27F36C56EE0, 0x27F36C56F18, 0x27F36C56F50,
                    0x27F36C56F88, 0x27F36C56FC0, 0x27F36C56FF8, 0x27F36C57030, 0x27F36C57068, 0x27F36C570A0,
                    0x27F36C570D8, 0x27F36C57110, 0x27F36C57148, 0x27F36C57180, 0x27F36C571B8, 0x27F36C571F0,
                    0x27F36C57228, 0x27F36C57260, 0x27F36C57298, 0x27F36C572D0, 0x27F36C57308, 0x27F36C57340,
                    0x27F36C57378, 0x27F36C573B0, 0x27F36C573E8, 0x27F36C57420, 0x27F36C57458, 0x27F36C57490,
                    0x27F36C574C8, 0x27F36C57500, 0x27F36C57538, 0x27F36C57570, 0x27F36C575A8, 0x27F36C575E0,
                    0x27F36C57618, 0x27F36C57650, 0x27F36C57688, 0x27F36C576C0, 0x27F36C576F8, 0x27F36C57730,
                    0x27F36C57768, 0x27F36C577A0, 0x27F36C577D8, 0x27F36C57810, 0x27F36C57848, 0x27F36C57880,
                    0x27F36C578B8, 0x27F36C578F0, 0x27F36C57928, 0x27F36C57960, 0x27F36C57998, 0x27F36C579D0,
                    0x27F36C57A08, 0x27F36C57A40, 0x27F36C57A78, 0x27F36C57AB0, 0x27F36C57AE8, 0x27F36C57B20,
                    0x27F36C57B58, 0x27F36C57B90, 0x27F36C57BC8, 0x27F36C57C00, 0x27F36C57C38, 0x27F36C57C70,
                    0x27F36C57CA8, 0x27F36C57CE0, 0x27F36C57D18, 0x27F36C57D50, 0x27F36C57D88, 0x27F36C57DC0,
                    0x27F36C57DF8, 0x27F36C57E30, 0x27F36C57E68, 0x27F36C57EA0, 0x27F36C57ED8, 0x27F36C57F10,
                    0x27F36C57F48, 0x27F36C57F80, 0x27F36C57FB8, 0x27F36C57FF0, 0x27F36C58028, 0x27F36C58060,
                    0x27F36C58098, 0x27F36C580D0, 0x27F36C58108, 0x27F36C58140, 0x27F36C58178, 0x27F36C581B0,
                    0x27F36C581E8, 0x27F36C58220, 0x27F36C58258, 0x27F36C58290, 0x27F36C582C8, 0x27F36C58300,
                    0x27F36C58338, 0x27F36C58370, 0x27F36C583A8, 0x27F36C583E0, 0x27F36C58418, 0x27F36C58450,
                    0x27F36C58488, 0x27F36C584C0, 0x27F36C584F8, 0x27F36C58530, 0x27F36C58568, 0x27F36C585A0,
                    0x27F36C585D8, 0x27F36C58610, 0x27F36C58648, 0x27F36C58680, 0x27F36C586B8, 0x27F36C586F0,
                    0x27F36C58728, 0x27F36C58760, 0x27F36C58798, 0x27F36C587D0, 0x27F36C58808, 0x27F36C58840,
                    0x27F36C58878, 0x27F36C588B0, 0x27F36C588E8, 0x27F36C58920, 0x27F36C58958, 0x27F36C58990,
                    0x27F36C589C8, 0x27F36C58A00, 0x27F36C58A38, 0x27F36C58A70, 0x27F36C58AA8, 0x27F36C58AE0,
                    0x27F36C58B18, 0x27F36C58B50, 0x27F36C58B88, 0x27F36C58BC0, 0x27F36C58BF8, 0x27F36C58C30,
                    0x27F36C58C68, 0x27F36C58CA0, 0x27F36C58CD8, 0x27F36C58D10, 0x27F36C58D48, 0x27F36C58D80,
                    0x27F36C58DB8, 0x27F36C58DF0, 0x27F36C58E28, 0x27F36C58E60, 0x27F36C58E98, 0x27F36C58ED0,
                    0x27F36C58F08, 0x27F36C58F40, 0x27F36C58F78, 0x27F36C58FB0, 0x27F36C58FE8, 0x27F36C59020,
                    0x27F36C59058, 0x27F36C59090, 0x27F36C590C8, 0x27F36C59100, 0x27F36C59138, 0x27F36C59170,
                    0x27F36C591A8, 0x27F36C591E0, 0x27F43106F24, 0x27F43106F28, 0x27F43106F2C, 0x27F43106F34,
                    0x27F43106F38, 0x27F43106F3C, 0x27F43106F40, 0x27F46C490DC, 0x27F46C490E0, 0x27F988023E0,
                    0x27F988023E4, 0x27F988023E8, 0x27F988023F0, 0x27F988023F4, 0x27F988023F8, 0x27F98802404,
                    0x27FC149004C, 0x27FC1490050, 0x27FD3B8A554, 0x27FD3B8A558, 0x27FD3B8A55C, 0x27FD3B8A560,
                    0x27FD3B8CE94, 0x27FD3B8CE98, 0x27FD3B8CE9C, 0x27FD3B8CEA0
                    # Add remaining addresses up to 2824
                ]  # Replace with full 2824 addresses
                min_coord = -11000.0  # Minimum coordinate range
                max_coord = 11000.0   # Maximum coordinate range
                offset, found_address = test_addresses_for_coordinates(process_handle, base_address, addresses, min_coord, max_coord)
                if offset:
                    print(f"Offset saved to offsets.txt: {hex(offset)}")
                    print(f"Found address: {hex(found_address)}")
                win32api.CloseHandle(process_handle)
            else:
                print("Failed to open process handle.")
        else:
            print("D2R not found. Ensure the game is running.")
    except Exception as e:
        print(f"Unexpected error: {str(e)}")