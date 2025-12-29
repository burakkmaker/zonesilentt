# ZoneSilent - Setup Guide

## ⚠️ IMPORTANT: Configure Your Google Maps API Key

Before running the app, you **MUST** configure your Google Maps API key.

### Step 1: Get Your API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable **Maps SDK for Android**
4. Navigate to **APIs & Services** → **Credentials**
5. Click **Create Credentials** → **API Key**
6. Copy your API key

### Step 2: Restrict Your API Key (Recommended)

1. Click on your API key to edit it
2. Under **Application restrictions**:
   - Select **Android apps**
   - Click **Add an item**
   - Package name: `com.burak.zonesilent`
   - Get your SHA-1 fingerprint:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Paste the SHA-1 fingerprint
3. Under **API restrictions**:
   - Select **Restrict key**
   - Enable **Maps SDK for Android**
4. Click **Save**

### Step 3: Add API Key to local.properties

Open or create the file `local.properties` in the project root and add:

```properties
sdk.dir=C\:\\Users\\burak\\AppData\\Local\\Android\\Sdk

# Add your Google Maps API key here
MAPS_API_KEY=YOUR_ACTUAL_API_KEY_HERE
```

**Replace `YOUR_ACTUAL_API_KEY_HERE` with your actual API key from Step 1.**

### Step 4: Sync and Build

1. Sync Gradle files in Android Studio
2. Clean and rebuild the project
3. Run the app

## Verification

After adding the API key:
- The map should load properly in the app
- You should be able to see map tiles
- You can tap on the map to add markers

## Troubleshooting

### Map shows blank/grey screen
- ✅ Verify API key is correct in `local.properties`
- ✅ Check SHA-1 fingerprint matches your keystore
- ✅ Ensure Maps SDK for Android is enabled
- ✅ Wait a few minutes for API key activation

### Build errors about MAPS_API_KEY
- ✅ Ensure `local.properties` exists in project root
- ✅ Verify property name is exactly `MAPS_API_KEY`
- ✅ Sync Gradle files

### "This app won't run without updating Google Play services"
- ✅ Update Google Play Services on your device/emulator
- ✅ Use a device with Google Play Services installed

## Security Notes

⚠️ **NEVER commit `local.properties` to version control!**

The file is already in `.gitignore`, but double-check before pushing to any repository.

## Need Help?

- [Maps SDK Documentation](https://developers.google.com/maps/documentation/android-sdk/start)
- [API Key Best Practices](https://developers.google.com/maps/api-security-best-practices)
