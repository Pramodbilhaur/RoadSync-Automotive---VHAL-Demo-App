# push_priv_app.ps1 - Fast Privileged System App Deployment Script for Android Automotive
# Usage: powershell -ExecutionPolicy Bypass -File .\push_priv_app.ps1

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host " ROADSYNC AUTOMOTIVE - PRIVILEGED APP DEPLOYMENT" -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

# Environment setup
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","User") + ";" + [System.Environment]::GetEnvironmentVariable("Path","Machine")

# Step 1: Check ADB connection
Write-Host "[1/5] Checking ADB device connection..." -ForegroundColor Yellow
$device = adb devices | Select-String "device$"
if (-not $device) {
    Write-Host "⚠️ No connected ADB emulator found. Starting emulator with -writable-system..." -ForegroundColor Yellow
    Remove-Item "$env:USERPROFILE\.android\avd\Automotive_1408p_landscape.avd\*.lock" -Force -Recurse -ErrorAction SilentlyContinue
    Start-Process "C:\Users\PramodKumar\AppData\Local\Android\Sdk\emulator\emulator.exe" -ArgumentList "-avd Automotive_1408p_landscape -writable-system -no-snapshot-load"
    adb wait-for-device
    Start-Sleep -Seconds 15
}

# Step 2: Enable root & remount
Write-Host "[2/5] Granting root access & remounting /system as RW..." -ForegroundColor Yellow
adb root
Start-Sleep -Seconds 2
$remountResult = adb remount 2>&1
if ($remountResult -like "*Read-only*" -or $remountResult -like "*bootloader unlocked*") {
    Write-Host "⚠️ Emulator was started without -writable-system. Relaunching emulator..." -ForegroundColor Yellow
    adb emu kill 2>$null | Out-Null
    Start-Sleep -Seconds 3
    Remove-Item "$env:USERPROFILE\.android\avd\Automotive_1408p_landscape.avd\*.lock" -Force -Recurse -ErrorAction SilentlyContinue
    Start-Process "C:\Users\PramodKumar\AppData\Local\Android\Sdk\emulator\emulator.exe" -ArgumentList "-avd Automotive_1408p_landscape -writable-system -no-snapshot-load"
    adb wait-for-device
    Start-Sleep -Seconds 15
    adb root
    Start-Sleep -Seconds 2
    adb remount
}

# Step 3: Create directories & Push system files
Write-Host "[3/5] Pushing APK and Privileged Permissions XML..." -ForegroundColor Yellow
adb shell mkdir -p /system/priv-app/MyAutomotiveApp

$apkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "❌ APK not found at $apkPath. Please build project in Android Studio first!" -ForegroundColor Red
    exit 1
}

# Push APK
adb push $apkPath /system/priv-app/MyAutomotiveApp/MyAutomotiveApp.apk
adb shell chmod 644 /system/priv-app/MyAutomotiveApp/MyAutomotiveApp.apk

# Push Permissions Whitelist XML
$xmlPath = "$PSScriptRoot\.artifacts\66a34e61-ac54-473d-a399-a8e5636353f3\scratch\privapp-permissions-com.example.vhaldemoapp.xml"
if (Test-Path $xmlPath) {
    adb push $xmlPath /system/etc/permissions/privapp-permissions-com.example.vhaldemoapp.xml
    adb shell chmod 644 /system/etc/permissions/privapp-permissions-com.example.vhaldemoapp.xml
}

# Step 4: Uninstall old user-space APK if present
Write-Host "[4/5] Removing user-space APK conflicts..." -ForegroundColor Yellow
adb uninstall com.example.vhaldemoapp 2>$null | Out-Null

# Step 5: Reboot to register system app
Write-Host "[5/5] Rebooting emulator to register Privileged System App..." -ForegroundColor Yellow
adb reboot
adb wait-for-device
Write-Host "Waiting for PackageManager boot completion..." -ForegroundColor Yellow
Start-Sleep -Seconds 20

# Grant runtime permissions for Driver User 10
adb shell pm grant --user 10 com.example.vhaldemoapp android.car.permission.CAR_SPEED 2>$null
adb shell pm grant --user 10 com.example.vhaldemoapp android.car.permission.CAR_ENERGY 2>$null
adb shell pm grant --user 10 com.example.vhaldemoapp android.car.permission.CONTROL_CAR_CLIMATE 2>$null

# Launch MainActivity
adb shell am start --user 10 -n com.example.vhaldemoapp/.MainActivity

Write-Host "====================================================" -ForegroundColor Green
Write-Host " SUCCESS! Privileged App Deployed to /system/priv-app/" -ForegroundColor Green
Write-Host " Verify path: adb shell pm path com.example.vhaldemoapp" -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
