# Bug Fix Planning Template

You are planning a bug fix for the BLE Explorer project.

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

## Bug Report
$ARGUMENTS

## Instructions
1. First, reproduce the bug or understand the failure mode
2. Explore the relevant code to identify the root cause
3. Create a detailed plan in `specs/bug-<name>.md` with:
   - Bug description and reproduction steps
   - Root cause analysis
   - Proposed fix
   - Files to be modified
   - Testing strategy to verify the fix
   - Regression prevention (add tests if applicable)
4. Output the spec file path when complete
