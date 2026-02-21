# Flash ELK Bridge firmware to ESP32-C3 SuperMini
# Usage: .\scripts\flash_bridge.ps1 [-Port COM5]
param(
    [string]$Port = "COM5"
)

$esptool = "$env:LOCALAPPDATA\Packages\PythonSoftwareFoundation.Python.3.11_qbz5n2kfra8p0\LocalCache\local-packages\Python311\Scripts\esptool.exe"
$buildDir = "$PSScriptRoot\..\firmware\elk-bridge\.pio\build\esp32c3"

if (-not (Test-Path $esptool)) {
    Write-Error "esptool.exe not found at: $esptool"
    exit 1
}

$files = @(
    "$buildDir\bootloader.bin",
    "$buildDir\partitions.bin",
    "$buildDir\boot_app0.bin",
    "$buildDir\firmware.bin"
)

foreach ($f in $files) {
    if (-not (Test-Path $f)) {
        Write-Error "Missing: $f"
        exit 1
    }
}

Write-Host "Flashing ELK Bridge (C3) to $Port..." -ForegroundColor Cyan
Write-Host "  bootloader.bin  -> 0x0000"
Write-Host "  partitions.bin  -> 0x8000"
Write-Host "  boot_app0.bin   -> 0xe000"
Write-Host "  firmware.bin    -> 0x10000"

& $esptool --chip esp32c3 -p $Port -b 460800 `
    --before default-reset --after hard-reset `
    write-flash -z --flash-mode dio --flash-freq 80m --flash-size 4MB `
    0x0000 "$buildDir\bootloader.bin" `
    0x8000 "$buildDir\partitions.bin" `
    0xe000 "$buildDir\boot_app0.bin" `
    0x10000 "$buildDir\firmware.bin"

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nFlash complete! Press RESET or unplug/replug the board." -ForegroundColor Green
} else {
    Write-Host "`nFlash FAILED. Is the board in download mode? (Hold BOOT while plugging in)" -ForegroundColor Red
}
