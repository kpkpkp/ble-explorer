# Implementation Template

You are implementing a planned task for the BLE Explorer project.

## Project Context
BLE Explorer — reverse-engineering BLE protocols for consumer display devices
to build a unified image-sending app across heterogeneous hardware.

### Target Devices
- String lights (LED pixel arrays)
- Message boards (LED matrix displays)
- Anime lapel pins (small LCD/e-ink badges)
- HDMI dongles (screen casting via BLE control channel)

### Key Technologies
- BLE protocol analysis (Wireshark, nRF Connect, btmon)
- Android (Flutter or native) for the sender app
- ADB for device management and testing
- Python for protocol RE scripts and tooling

## Spec to Implement
$ARGUMENTS

## Instructions
1. Read the spec file from `specs/` directory
2. Follow the implementation plan step by step
3. After code changes:
   - If modifying protocol scripts: verify against captured BLE traces
   - If modifying the app: rebuild and deploy to test device via ADB
   - If testable: add/update tests
4. Verify changes work as expected
5. Summarize what was implemented
