# Chore Planning Template

You are planning a development task for the BLE Explorer project.

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

## Task
$ARGUMENTS

## Instructions
1. First, explore the relevant parts of the codebase to understand the current state
2. Identify affected files and components
3. Create a detailed plan in `specs/` with:
   - Clear problem statement
   - Proposed solution approach
   - Files to be modified/created
   - Testing strategy
   - Rollback considerations (if applicable)
4. Output the spec file path when complete
