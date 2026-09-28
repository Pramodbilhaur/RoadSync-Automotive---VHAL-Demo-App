# simulate_drive.ps1 - Simulates driving speed and battery updates
Write-Host "Starting Vehicle Sensor Simulation..." -ForegroundColor Green

# 1. Put Car in Drive (Gear = 8)
adb shell cmd car_service inject-vhal-event GEAR_SELECTION 0 8
Start-Sleep -Seconds 1

# 2. Accelerate from 0 to 100 km/h
for ($speedKmh = 0; $speedKmh -le 100; $speedKmh += 10) {
    # Convert km/h to m/s for VHAL
    $speedMps = $speedKmh / 3.6
    Write-Host "Current Speed: $speedKmh km/h ($([math]::Round($speedMps,2)) m/s)" -ForegroundColor Yellow
    
    adb shell cmd car_service inject-continuous-events 0x11600207 $speedMps
    Start-Sleep -Milliseconds 500
}

Write-Host "Cruising at 100 km/h..." -ForegroundColor Green
Start-Sleep -Seconds 3

# 3. Decelerate to 0 km/h
for ($speedKmh = 100; $speedKmh -ge 0; $speedKmh -= 20) {
    $speedMps = $speedKmh / 3.6
    Write-Host "Braking... Speed: $speedKmh km/h" -ForegroundColor Red
    
    adb shell cmd car_service inject-continuous-events 0x11600207 $speedMps
    Start-Sleep -Milliseconds 500
}

# 4. Put Car in Park (Gear = 4)
adb shell cmd car_service inject-vhal-event GEAR_SELECTION 0 4
Write-Host "Vehicle Parked." -ForegroundColor Green