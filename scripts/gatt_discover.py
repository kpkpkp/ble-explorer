#!/usr/bin/env python3
"""GATT service/characteristic/descriptor discovery from HCI snoop logs.

Extracts the full ATT attribute database by parsing:
- Read By Group Type responses (service discovery)
- Read By Type responses (characteristic discovery)
- Find Information responses (descriptor discovery)
- Read responses (attribute values)
- Write requests/commands and notifications

Usage:
    python3 scripts/gatt_discover.py captures/btsnoop_p2red_3.log
    python3 scripts/gatt_discover.py captures/btsnoop_p2red_3.log --handle 2
    python3 scripts/gatt_discover.py captures/btsnoop_p2red_3.log --all-ops
"""

import struct
import sys
import argparse
from collections import defaultdict
from datetime import datetime

BTSNOOP_MAGIC = b"btsnoop\x00"

# ATT opcodes
ATT_OPS = {
    0x01: "ERROR_RSP",
    0x02: "EXCHANGE_MTU_REQ",
    0x03: "EXCHANGE_MTU_RSP",
    0x04: "FIND_INFO_REQ",
    0x05: "FIND_INFO_RSP",
    0x06: "FIND_BY_TYPE_VALUE_REQ",
    0x07: "FIND_BY_TYPE_VALUE_RSP",
    0x08: "READ_BY_TYPE_REQ",
    0x09: "READ_BY_TYPE_RSP",
    0x0A: "READ_REQ",
    0x0B: "READ_RSP",
    0x0C: "READ_BLOB_REQ",
    0x0D: "READ_BLOB_RSP",
    0x10: "READ_BY_GROUP_TYPE_REQ",
    0x11: "READ_BY_GROUP_TYPE_RSP",
    0x12: "WRITE_REQ",
    0x13: "WRITE_RSP",
    0x16: "PREPARE_WRITE_REQ",
    0x17: "PREPARE_WRITE_RSP",
    0x18: "EXECUTE_WRITE_REQ",
    0x19: "EXECUTE_WRITE_RSP",
    0x1B: "HANDLE_VALUE_NTF",
    0x1D: "HANDLE_VALUE_IND",
    0x1E: "HANDLE_VALUE_CFM",
    0x52: "WRITE_CMD",
    0xD2: "SIGNED_WRITE_CMD",
}

# Well-known UUIDs
KNOWN_UUIDS = {
    0x1800: "Generic Access",
    0x1801: "Generic Attribute",
    0x180A: "Device Information",
    0x180F: "Battery Service",
    0x2800: "Primary Service",
    0x2801: "Secondary Service",
    0x2802: "Include",
    0x2803: "Characteristic Declaration",
    0x2900: "Characteristic Extended Properties",
    0x2901: "Characteristic User Description",
    0x2902: "Client Characteristic Configuration (CCCD)",
    0x2903: "Server Characteristic Configuration",
    0x2904: "Characteristic Presentation Format",
    0x2A00: "Device Name",
    0x2A01: "Appearance",
    0x2A02: "Peripheral Privacy Flag",
    0x2A03: "Reconnection Address",
    0x2A04: "Peripheral Preferred Connection Parameters",
    0x2A05: "Service Changed",
    0x2A19: "Battery Level",
    0x2A24: "Model Number String",
    0x2A25: "Serial Number String",
    0x2A26: "Firmware Revision String",
    0x2A27: "Hardware Revision String",
    0x2A28: "Software Revision String",
    0x2A29: "Manufacturer Name String",
    0xFFF0: "ELK-BLEDOM LED Control",
    0xFFF1: "ELK-BLEDOM (unknown)",
    0xFFF2: "ELK-BLEDOM (unknown)",
    0xFFF3: "ELK-BLEDOM Write Control",
    0xFFF4: "ELK-BLEDOM Notify Status",
    0xFFE0: "ELK-BLEDOM Alt Service",
    0xFFE1: "ELK-BLEDOM Alt Write",
    0xFFE2: "ELK-BLEDOM Alt Notify",
}

CHAR_PROPERTIES = {
    0x01: "BROADCAST",
    0x02: "READ",
    0x04: "WRITE_NO_RESP",
    0x08: "WRITE",
    0x10: "NOTIFY",
    0x20: "INDICATE",
    0x40: "AUTH_SIGNED_WRITE",
    0x80: "EXTENDED_PROPS",
}


def format_uuid(data):
    """Format a UUID from raw bytes (2, 4, or 16 bytes)."""
    if len(data) == 2:
        val = struct.unpack_from("<H", data, 0)[0]
        name = KNOWN_UUIDS.get(val, "")
        if name:
            return f"0x{val:04X} ({name})"
        return f"0x{val:04X}"
    elif len(data) == 4:
        val = struct.unpack_from("<I", data, 0)[0]
        return f"0x{val:08X}"
    elif len(data) == 16:
        # Standard BLE 128-bit UUID: little-endian
        b = data[::-1]
        s = (f"{b[0:4].hex()}-{b[4:6].hex()}-{b[6:8].hex()}-"
             f"{b[8:10].hex()}-{b[10:16].hex()}")
        # Check if it's a standard Bluetooth base UUID
        if s.endswith("-0000-1000-8000-00805f9b34fb"):
            short = struct.unpack_from("<H", data, 12)[0]
            name = KNOWN_UUIDS.get(short, "")
            if name:
                return f"0x{short:04X} ({name})"
            return f"0x{short:04X}"
        return s
    return data.hex()


def format_char_props(props_byte):
    """Format characteristic properties byte."""
    flags = []
    for bit, name in CHAR_PROPERTIES.items():
        if props_byte & bit:
            flags.append(name)
    return " | ".join(flags) if flags else f"0x{props_byte:02X}"


def parse_snoop(filepath, filter_handle=None, show_all=False):
    """Parse btsnoop and extract all ATT operations grouped by connection."""
    with open(filepath, "rb") as f:
        data = f.read()

    if not data.startswith(BTSNOOP_MAGIC):
        print(f"ERROR: Not a valid btsnoop file")
        return

    version = struct.unpack_from(">I", data, 8)[0]
    datalink = struct.unpack_from(">I", data, 12)[0]
    is_h4 = (datalink == 1002)
    offset = 16

    connections = {}  # conn_handle -> MAC
    # Per-connection attribute database
    services = defaultdict(list)       # conn_handle -> [{start, end, uuid}]
    characteristics = defaultdict(list) # conn_handle -> [{handle, props, value_handle, uuid}]
    descriptors = defaultdict(list)    # conn_handle -> [{handle, uuid}]
    att_values = defaultdict(dict)     # conn_handle -> {handle -> value}
    att_ops_log = defaultdict(list)    # conn_handle -> [(ts, opcode, data)]
    notifications = defaultdict(list)  # conn_handle -> [(ts, handle, data)]
    writes = defaultdict(list)         # conn_handle -> [(ts, handle, data)]

    pkt_count = 0
    unix_epoch_offset = 0x00dcddb30f2f8000

    while offset + 24 <= len(data):
        orig_len, incl_len, flags, drops = struct.unpack_from(">IIII", data, offset)
        ts_raw = struct.unpack_from(">Q", data, offset + 16)[0]
        offset += 24

        if offset + incl_len > len(data):
            break

        raw_pkt = data[offset:offset + incl_len]
        offset += incl_len
        pkt_count += 1

        # Determine packet type
        if is_h4 and len(raw_pkt) >= 1:
            h4_type = raw_pkt[0]
            pkt_data = raw_pkt[1:]
        else:
            is_received = flags & 1
            is_cmd_evt = flags & 2
            h4_type = 0x04 if (is_received and is_cmd_evt) else (0x02 if not is_cmd_evt else 0x01)
            pkt_data = raw_pkt

        # Track connections from HCI events
        if h4_type == 0x04 and len(pkt_data) >= 4:
            evt_code = pkt_data[0]
            if evt_code == 0x3E:  # LE Meta
                subevent = pkt_data[2]
                if subevent == 0x01 and len(pkt_data) >= 13:
                    status = pkt_data[3]
                    if status == 0:
                        ch = struct.unpack_from("<H", pkt_data, 4)[0]
                        mac = ":".join(f"{b:02X}" for b in reversed(pkt_data[7:13]))
                        connections[ch] = mac
                elif subevent == 0x0A and len(pkt_data) >= 31:
                    status = pkt_data[3]
                    if status == 0:
                        ch = struct.unpack_from("<H", pkt_data, 4)[0]
                        mac = ":".join(f"{b:02X}" for b in reversed(pkt_data[8:14]))
                        connections[ch] = mac

        # Parse ACL -> ATT
        if h4_type != 0x02 or len(pkt_data) < 9:
            continue

        handle_flags = struct.unpack_from("<H", pkt_data, 0)[0]
        conn_handle = handle_flags & 0x0FFF
        pb_flag = (handle_flags >> 12) & 0x03

        if filter_handle is not None and conn_handle != filter_handle:
            continue

        acl_len = struct.unpack_from("<H", pkt_data, 2)[0]
        l2cap = pkt_data[4:]
        if len(l2cap) < 4:
            continue
        l2cap_len = struct.unpack_from("<H", l2cap, 0)[0]
        l2cap_cid = struct.unpack_from("<H", l2cap, 2)[0]

        if l2cap_cid != 0x0004:  # ATT channel only
            continue

        att = l2cap[4:]
        if len(att) < 1:
            continue

        att_opcode = att[0]
        ts_us = ts_raw - unix_epoch_offset
        ts_sec = ts_us / 1_000_000
        try:
            timestamp = datetime.utcfromtimestamp(ts_sec)
        except (OSError, ValueError):
            timestamp = None

        op_name = ATT_OPS.get(att_opcode, f"0x{att_opcode:02X}")

        if show_all:
            att_ops_log[conn_handle].append((timestamp, op_name, att))

        # --- Read By Group Type Response (service discovery) ---
        if att_opcode == 0x11 and len(att) >= 6:
            attr_len = att[1]
            pos = 2
            while pos + attr_length_check(attr_len) <= len(att):
                start_h = struct.unpack_from("<H", att, pos)[0]
                end_h = struct.unpack_from("<H", att, pos + 2)[0]
                uuid_bytes = att[pos + 4:pos + attr_len]
                uuid_str = format_uuid(uuid_bytes)
                services[conn_handle].append({
                    "start": start_h, "end": end_h, "uuid": uuid_str,
                    "uuid_raw": uuid_bytes.hex(),
                })
                pos += attr_len

        # --- Read By Type Response (characteristic discovery) ---
        if att_opcode == 0x09 and len(att) >= 7:
            attr_len = att[1]
            pos = 2
            while pos + attr_length_check(attr_len) <= len(att):
                attr_handle = struct.unpack_from("<H", att, pos)[0]
                # Characteristic declaration: props(1) + value_handle(2) + uuid(2 or 16)
                if attr_len >= 7:  # min: handle(2) + props(1) + value_handle(2) + uuid(2)
                    props = att[pos + 2]
                    val_handle = struct.unpack_from("<H", att, pos + 3)[0]
                    uuid_bytes = att[pos + 5:pos + attr_len]
                    uuid_str = format_uuid(uuid_bytes)
                    characteristics[conn_handle].append({
                        "handle": attr_handle,
                        "properties": props,
                        "properties_str": format_char_props(props),
                        "value_handle": val_handle,
                        "uuid": uuid_str,
                        "uuid_raw": uuid_bytes.hex(),
                    })
                pos += attr_len

        # --- Find Information Response (descriptor discovery) ---
        if att_opcode == 0x05 and len(att) >= 6:
            fmt = att[1]  # 1 = 16-bit UUIDs, 2 = 128-bit UUIDs
            uuid_len = 2 if fmt == 1 else 16
            entry_len = 2 + uuid_len
            pos = 2
            while pos + entry_len <= len(att):
                desc_handle = struct.unpack_from("<H", att, pos)[0]
                uuid_bytes = att[pos + 2:pos + 2 + uuid_len]
                uuid_str = format_uuid(uuid_bytes)
                descriptors[conn_handle].append({
                    "handle": desc_handle,
                    "uuid": uuid_str,
                    "uuid_raw": uuid_bytes.hex(),
                })
                pos += entry_len

        # --- Read Response ---
        if att_opcode == 0x0B and len(att) >= 2:
            # We'd need to track the preceding READ_REQ to know which handle
            pass

        # --- Write Request / Write Command ---
        if att_opcode in (0x12, 0x52) and len(att) >= 3:
            w_handle = struct.unpack_from("<H", att, 1)[0]
            w_data = att[3:]
            writes[conn_handle].append((timestamp, w_handle, w_data))

        # --- Handle Value Notification ---
        if att_opcode == 0x1B and len(att) >= 3:
            n_handle = struct.unpack_from("<H", att, 1)[0]
            n_data = att[3:]
            notifications[conn_handle].append((timestamp, n_handle, n_data))

        # --- Handle Value Indication ---
        if att_opcode == 0x1D and len(att) >= 3:
            n_handle = struct.unpack_from("<H", att, 1)[0]
            n_data = att[3:]
            notifications[conn_handle].append((timestamp, n_handle, n_data))

    # --- Display results ---
    print(f"Parsed {pkt_count} HCI packets")
    print(f"Connections: {len(connections)}")
    for ch, mac in sorted(connections.items()):
        print(f"  handle={ch} -> {mac}")
    print()

    for ch in sorted(set(list(services.keys()) + list(characteristics.keys()) +
                         list(writes.keys()) + list(notifications.keys()))):
        mac = connections.get(ch, f"handle:{ch}")
        print(f"{'=' * 80}")
        print(f" Connection handle={ch}  MAC={mac}")
        print(f"{'=' * 80}")

        # Services
        if services[ch]:
            print(f"\n  SERVICES ({len(services[ch])})")
            print(f"  {'Handle Range':<18} {'UUID'}")
            print(f"  {'-' * 18} {'-' * 50}")
            for s in services[ch]:
                print(f"  0x{s['start']:04X}–0x{s['end']:04X}      {s['uuid']}")

        # Characteristics
        if characteristics[ch]:
            print(f"\n  CHARACTERISTICS ({len(characteristics[ch])})")
            print(f"  {'Decl':>6} {'Value':>6}  {'Properties':<30} {'UUID'}")
            print(f"  {'-' * 6} {'-' * 6}  {'-' * 30} {'-' * 40}")
            for c in characteristics[ch]:
                print(f"  0x{c['handle']:04X} 0x{c['value_handle']:04X}  "
                      f"{c['properties_str']:<30} {c['uuid']}")

        # Descriptors
        if descriptors[ch]:
            print(f"\n  DESCRIPTORS ({len(descriptors[ch])})")
            print(f"  {'Handle':>8}  {'UUID'}")
            print(f"  {'-' * 8}  {'-' * 40}")
            for d in descriptors[ch]:
                print(f"  0x{d['handle']:04X}    {d['uuid']}")

        # Write summary by handle
        if writes[ch]:
            handle_counts = defaultdict(int)
            for _, wh, _ in writes[ch]:
                handle_counts[wh] += 1
            print(f"\n  WRITES ({len(writes[ch])} total)")
            print(f"  {'Handle':>8}  {'Count':>6}  {'Sample Data'}")
            print(f"  {'-' * 8}  {'-' * 6}  {'-' * 40}")
            for wh in sorted(handle_counts.keys()):
                cnt = handle_counts[wh]
                sample = next(d for _, h, d in writes[ch] if h == wh)
                print(f"  0x{wh:04X}    {cnt:6d}  {sample.hex()[:40]}")

        # Notification summary
        if notifications[ch]:
            handle_counts = defaultdict(int)
            for _, nh, _ in notifications[ch]:
                handle_counts[nh] += 1
            print(f"\n  NOTIFICATIONS ({len(notifications[ch])} total)")
            print(f"  {'Handle':>8}  {'Count':>6}  {'Sample Data'}")
            print(f"  {'-' * 8}  {'-' * 6}  {'-' * 40}")
            for nh in sorted(handle_counts.keys()):
                cnt = handle_counts[nh]
                sample = next(d for _, h, d in notifications[ch] if h == nh)
                print(f"  0x{nh:04X}    {cnt:6d}  {sample.hex()[:40]}")

        # Full ATT operation log
        if show_all and att_ops_log[ch]:
            print(f"\n  ALL ATT OPERATIONS ({len(att_ops_log[ch])})")
            for ts, op, raw_att in att_ops_log[ch][:100]:
                ts_str = ts.strftime("%H:%M:%S.%f")[:12] if ts else "?"
                print(f"  {ts_str}  {op:<28s}  {raw_att[:20].hex()}")
            if len(att_ops_log[ch]) > 100:
                print(f"  ... and {len(att_ops_log[ch]) - 100} more")

        print()


def attr_length_check(attr_len):
    """Minimum bytes needed for an attribute entry."""
    return max(attr_len, 4)  # at least start(2) + end(2) or handle(2) + uuid(2)


def main():
    parser = argparse.ArgumentParser(
        description="Discover GATT services, characteristics, and descriptors from HCI snoop log"
    )
    parser.add_argument("file", help="btsnoop_hci.log file to analyze")
    parser.add_argument("--handle", type=int, help="Filter by connection handle")
    parser.add_argument("--all-ops", action="store_true",
                        help="Show all ATT operations (verbose)")
    args = parser.parse_args()

    parse_snoop(args.file, filter_handle=args.handle, show_all=args.all_ops)


if __name__ == "__main__":
    main()
