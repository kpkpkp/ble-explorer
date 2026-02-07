# Feature Planning Template

You are planning a new feature for the BLE Explorer project.

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

## Feature Request
$ARGUMENTS

## Instructions
1. First, understand the feature requirements and user story
2. Explore the existing codebase for related functionality
3. Create a detailed plan in `specs/feature-<name>.md` with:
   - Feature description and user story
   - Technical design (architecture decisions)
   - Database changes (if any)
   - API endpoints (if any)
   - UI changes (if any)
   - Files to be created/modified
   - Testing strategy
   - Deployment considerations
4. Output the spec file path when complete
