#!/usr/bin/env python3
"""Debug script to inspect btsnoop HCI log packet types."""
import struct
import sys

filepath = sys.argv[1] if len(sys.argv) > 1 else "captures/btsnoop_p2red.log"
data = open(filepath, "rb").read()
print(f"File size: {len(data):,} bytes")
print(f"Magic: {data[:8]}")

offset = 16
evt_counts = {}
le_meta_subs = {}
att_ops = {}
acl_writes = 0
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

    is_recv = flags & 1
    is_evt = flags & 2
    key = f"recv={is_recv},cmd_evt={is_evt}"
    evt_counts[key] = evt_counts.get(key, 0) + 1

    # HCI Events (received, command/event type)
    if is_recv and is_evt and len(pkt) >= 3:
        evt_code = pkt[0]
        if evt_code == 0x3E:  # LE Meta Event
            sub = pkt[2]
            le_meta_subs[sub] = le_meta_subs.get(sub, 0) + 1
            # LE Connection Complete
            if sub == 0x01 and len(pkt) >= 14:
                status = pkt[3]
                handle = struct.unpack_from("<H", pkt, 4)[0]
                addr = ":".join(f"{b:02X}" for b in reversed(pkt[7:13]))
                print(f"  LE Connection Complete: status={status} handle=0x{handle:04x} addr={addr}")
            # LE Enhanced Connection Complete
            elif sub == 0x0A and len(pkt) >= 31:
                status = pkt[3]
                handle = struct.unpack_from("<H", pkt, 4)[0]
                addr = ":".join(f"{b:02X}" for b in reversed(pkt[8:14]))
                print(f"  LE Enhanced Connection Complete: status={status} handle=0x{handle:04x} addr={addr}")

    # ACL data (not command/event)
    if not is_evt and len(pkt) >= 9:
        l2cap_cid = struct.unpack_from("<H", pkt, 6)[0]
        if l2cap_cid == 4:  # ATT channel
            att_op = pkt[8]
            att_ops[att_op] = att_ops.get(att_op, 0) + 1
            if att_op in (0x12, 0x52):
                acl_writes += 1
                if acl_writes <= 10:
                    att_handle = struct.unpack_from("<H", pkt, 9)[0]
                    write_data = pkt[11:min(11 + 20, len(pkt))]
                    print(f"  GATT Write: op=0x{att_op:02x} att_handle=0x{att_handle:04x} data={write_data.hex()}")

print(f"\nTotal packets: {total}")
print(f"Packet types: {evt_counts}")
print(f"LE Meta subevents: {le_meta_subs}")
print(f"ATT opcodes: {att_ops}")
print(f"GATT writes: {acl_writes}")
