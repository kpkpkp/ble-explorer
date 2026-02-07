# Deployment Template

You are deploying the BLE Explorer app to test devices via ADB.

## Deployment Target
$ARGUMENTS

## Pre-Deployment Checklist
1. Verify APK is built and up to date (rebuild if needed)
2. Check connected devices with `adb devices`
3. Review recent changes with `git diff`

## Deployment Steps

1. **List available devices**:
   ```bash
   python3 scripts/adb-inventory.py
   ```

2. **Install APK to target device(s)**:
   ```bash
   # Single device
   adb -s <device> install -r build/app/outputs/flutter-apk/app-debug.apk

   # All connected devices
   python3 scripts/adb-inventory.py --install-all
   ```

3. **Launch the app**:
   ```bash
   adb -s <device> shell am start -n <package>/<activity>
   ```

4. **Verify deployment**:
   - Confirm app launches on device
   - Check logcat for startup errors: `adb -s <device> logcat -s flutter`
   - Test BLE scanning works (device permissions granted)

## Rollback
If issues arise:
1. Uninstall: `adb -s <device> uninstall <package>`
2. Install previous APK from git history
