# Testing Template

You are running tests for the BLE Explorer project.

## Project Context
- Protocol RE scripts are in `scripts/` or `tools/`
- BLE traces/captures may be in `captures/` or `traces/`
- App tests follow the framework's conventions (Flutter test, JUnit, etc.)

## Test Target
$ARGUMENTS

## Instructions

### Protocol Tests
Verify BLE protocol implementations against captured traces:
```bash
# Run protocol unit tests
python3 -m pytest tests/ -v

# Test a specific protocol decoder
python3 -m pytest tests/test_<device>_protocol.py -v

# Validate against a captured trace
python3 scripts/replay_trace.py captures/<device>.pcap
```

### App Tests
```bash
# Flutter unit/widget tests
flutter test

# Android instrumentation tests
./gradlew connectedAndroidTest
```

### Live Device Tests
Verify BLE communication with a real device:
```bash
# Scan for target devices
python3 scripts/ble_scan.py

# Send test payload to device
python3 scripts/send_test_image.py --device <mac_address>
```

### Report
- Report pass/fail counts and any error details
- For protocol tests: note which message types are validated
- For live tests: note device model and firmware version tested
