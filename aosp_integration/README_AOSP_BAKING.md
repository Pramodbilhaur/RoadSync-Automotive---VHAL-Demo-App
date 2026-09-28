# Permanent Production AOSP Image Baking Guide

This directory contains the production-grade blueprint files to bake **VHALDemoApp** directly into an AOSP Automotive system image (`system.img`), ensuring the app survives cold boots, factory resets, and image reloads permanently.

---

## Steps to Bake VHALDemoApp into AOSP:

1. **Copy the integration folder to your AOSP build tree:**
   ```bash
   cp -r aosp_integration /your_aosp_root/device/generic/automotive/vhaldemoapp
   ```

2. **Copy your compiled APK into the directory:**
   ```bash
   cp app/build/outputs/apk/debug/app-debug.apk /your_aosp_root/device/generic/automotive/vhaldemoapp/app-debug.apk
   ```

3. **Include the module in your product configuration (`device.mk`):**
   ```make
   PRODUCT_PACKAGES += \
       VHALDemoApp
   ```

4. **Build the AOSP Automotive Emulator Target:**
   ```bash
   source build/envsetup.sh
   lunch sdk_car_x86_64-userdebug
   m -j$(nproc)
   ```

5. **Launch the custom built AOSP Automotive emulator:**
   ```bash
   emulator
   ```

---

## Fast Local Development Workflow (Without AOSP Rebuilds):

For daily development iterations, use the automated script created in the project root:

```powershell
.\push_priv_app.ps1
```
