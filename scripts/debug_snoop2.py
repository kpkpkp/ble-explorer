#!/usr/bin/env python3
"""Deep debug of btsnoop HCI log format."""
import struct
import sys

filepath = sys.argv[1] if len(sys.argv) > 1 else "captures/btsnoop_p2red.log"
data = open(filepath, "rb").read()

version = struct.unpack_from(">I", data, 8)[0]
datalink = struct.unpack_from(">I", data, 12)[0]
print(f"Version: {version}, Datalink type: {datalink}")
print(f"  (1001=H1, 1002=H4/HCI UART, 2001=BCSP, 1003=H5)")

offset = 16
evt_codes = {}
sent_cmd_opcodes = {}
acl_details = []
total = 0

while offset + 24 <= len(data):
    orig_len, incl_len, flags, drops = struct.unpack_from(">IIII", data, offset)
    ts = struct.unpack_from(">Q", data, offset + 16)[0]
    offset += 24
    if offset + incl_len > len(data):
        break
    pkt = data[offset:offset + incl_len]
    offset += incl_len
    total += 1
    if total > 500:
        continue  # just count after first 500

    is_recv = flags & 1
    is_evt = flags & 2

    # For datalink 1002 (H4), first byte is packet type indicator:
    # 0x01=Command, 0x02=ACL, 0x03=SCO, 0x04=Event
    if datalink == 1002 and len(pkt) >= 1:
        pkt_type = pkt[0]
        inner = pkt[1:]
        if pkt_type == 0x04 and len(inner) >= 2:  # Event
            evt_code = inner[0]
            evt_codes[evt_code] = evt_codes.get(evt_code, 0) + 1
            if evt_code == 0x3E and len(inner) >= 3:  # LE Meta
                sub = inner[2]
                print(f"  [H4] LE Meta sub=0x{sub:02x} at pkt {total}")
                if sub == 0x01 and len(inner) >= 14:
                    handle = struct.unpack_from("<H", inner, 4)[0]
                    addr = ":".join(f"{b:02X}" for b in reversed(inner[7:13]))
                    print(f"    Connection: handle=0x{handle:04x} addr={addr}")
        elif pkt_type == 0x02 and len(inner) >= 9:  # ACL
            l2cap_cid = struct.unpack_from("<H", inner, 6)[0]
            if l2cap_cid == 4:
                att_op = inner[8]
                if att_op in (0x12, 0x52) and len(inner) >= 11:
                    att_handle = struct.unpack_from("<H", inner, 9)[0]
                    wd = inner[11:min(30, len(inner))]
                    print(f"  [H4] GATT Write: att_handle=0x{att_handle:04x} data={wd.hex()}")
                    acl_details.append(wd)
        elif pkt_type == 0x01 and len(inner) >= 3:  # Command
            opcode = struct.unpack_from("<H", inner, 0)[0]
            sent_cmd_opcodes[opcode] = sent_cmd_opcodes.get(opcode, 0) + 1
    else:
        # datalink 1001 (H1): no packet type indicator, use flags
        if is_recv and is_evt and len(pkt) >= 2:
            evt_code = pkt[0]
            evt_codes[evt_code] = evt_codes.get(evt_code, 0) + 1

    if total <= 5:
        print(f"  Pkt {total}: flags=0x{flags:08x} len={incl_len} first_bytes={pkt[:8].hex()}")

print(f"\nDatalink: {datalink}")
print(f"Total packets (first 500 inspected in detail): {total}")
print(f"HCI Event codes: {dict(sorted(evt_codes.items()))}")
if sent_cmd_opcodes:
    print(f"HCI Command opcodes (top 10): {dict(sorted(sent_cmd_opcodes.items(), key=lambda x:-x[1])[:10])}")
print(f"GATT writes found: {len(acl_details)}")
