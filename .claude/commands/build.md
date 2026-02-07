# Build Template

You are building the BLE Explorer app or tooling.

## Build Targets

### Android App (Flutter)
```bash
flutter build apk --debug
```

### Android App (Native)
```bash
./gradlew assembleDebug
```

### Python Protocol Tools
```bash
pip install -r requirements.txt
python -m pytest tests/
```

## Build Arguments
$ARGUMENTS

## Instructions
1. Detect the project type (Flutter, native Android, Python scripts)
2. Run the appropriate build command
3. Verify the build:
   - Check for build errors in output
   - Confirm output artifacts exist (APK, .pyc, etc.)
   - Report build size and any warnings
4. If build fails, diagnose and fix

## Notes
- Flutter builds require `flutter` in PATH
- Native Android builds require Android SDK and Gradle
- Python tools should work with Python 3.10+
