# Building and Testing CyKrome Launcher

This guide will walk you through building the launcher and installing it on your Android device.

## Prerequisites

1. **Android Studio** (Arctic Fox or later recommended)
   - Download from: https://developer.android.com/studio
   - Make sure you have the latest Android SDK installed

2. **Android Device** (Physical device recommended)
   - Android 8.0 (API 26) or higher
   - USB debugging enabled (see below)

3. **USB Cable** to connect your phone to your computer

## Step 1: Enable Developer Options on Your Phone

1. Go to **Settings** > **About Phone**
2. Find **Build Number** (may be under Software Information)
3. Tap **Build Number** 7 times until you see "You are now a developer!"

## Step 2: Enable USB Debugging

1. Go to **Settings** > **Developer Options** (or **System** > **Developer Options**)
2. Enable **USB Debugging**
3. Enable **Install via USB** (if available)
4. Connect your phone to your computer via USB

## Step 3: Open Project in Android Studio

1. Launch **Android Studio**
2. Click **File** > **Open**
3. Navigate to the `CyKrome-Launcher` folder
4. Click **OK** to open the project
5. Wait for Gradle sync to complete (may take a few minutes on first open)

## Step 4: Verify Device Connection

1. In Android Studio, look at the bottom toolbar
2. You should see your device name in the device selector
3. If not visible:
   - Click the device dropdown
   - Select your connected device
   - If device doesn't appear, check USB debugging is enabled

**Alternative: Check via ADB**
```bash
# Open terminal in Android Studio or command prompt
adb devices
# You should see your device listed
```

## Step 5: Build the Project

### Option A: Build via Android Studio (Recommended)

1. Click **Build** > **Make Project** (or press `Ctrl+F9` / `Cmd+F9`)
2. Wait for build to complete
3. Check the **Build** tab at the bottom for any errors

### Option B: Build via Command Line

```bash
# Navigate to project directory
cd /path/to/CyKrome-Launcher

# Build debug APK
./gradlew assembleDebug

# Or build release APK (requires signing)
./gradlew assembleRelease
```

The APK will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## Step 6: Install on Your Phone

### Option A: Run Directly from Android Studio (Easiest)

1. Make sure your device is selected in the device dropdown
2. Click the **Run** button (green play icon) or press `Shift+F10` / `Ctrl+R`
3. Android Studio will build and install automatically
4. The app will launch on your device

### Option B: Install APK Manually

1. Transfer the APK file to your phone (via USB, email, or cloud storage)
2. On your phone, open **Files** app
3. Navigate to the APK location
4. Tap the APK file
5. If prompted, allow installation from unknown sources
6. Tap **Install**

### Option C: Install via ADB

```bash
# Install debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or if you need to reinstall
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Step 7: Set as Default Launcher

**Important:** You need to set CyKrome Launcher as your default launcher to use it.

1. After installation, you'll see a dialog asking to choose a launcher
2. Select **CyKrome Launcher**
3. Tap **Always** (not "Just once")

**If you missed the dialog:**
1. Go to **Settings** > **Apps** > **Default Apps** (or **Apps** > **Default Apps**)
2. Tap **Home App** (or **Launcher**)
3. Select **CyKrome Launcher**

## Step 8: Grant Permissions

For full functionality, grant these permissions:

### Notification Access (for badges)
1. Go to **Settings** > **Apps** > **Special Access** > **Notification Access**
2. Enable **CyKrome Launcher**

### File Access (for backup/restore)
- Usually granted automatically, but may need to allow access to files

## Step 9: Testing the Launcher

### Basic Functionality
- ✅ Home screen displays
- ✅ App drawer opens (swipe up or tap app drawer icon)
- ✅ Apps can be launched
- ✅ Multiple home screens (swipe left/right)

### Settings
1. Long press on home screen or open app drawer
2. Tap **Settings** (or use gesture if configured)
3. Test various settings:
   - Change grid size
   - Adjust icon size
   - Toggle icon labels
   - Configure gestures

### Gestures
- **Swipe Up**: Should open app drawer (default)
- **Swipe Down**: Should show notifications (default)
- **Double Tap**: Configure in settings

### Backup/Restore
1. Go to **Settings** > **Backup & Restore**
2. Tap **Backup Settings** - creates a JSON file
3. Tap **Restore Settings** - can restore from:
   - CyKrome backup files (.json)
   - Nova Launcher backup files (.novabackup)

### Hide Apps
1. Go to **Settings** > **Hide Apps**
2. Select apps to hide
3. Verify they don't appear in app drawer

## Troubleshooting

### Build Errors

**"SDK not found"**
- Go to **File** > **Project Structure** > **SDK Location**
- Set Android SDK location

**"Gradle sync failed"**
- Click **File** > **Invalidate Caches / Restart**
- Select **Invalidate and Restart**

**"Minimum SDK version"**
- Make sure your device is Android 8.0+ (API 26+)

### Installation Issues

**"App not installed"**
- Uninstall any previous version first
- Check if device meets minimum requirements (Android 8.0+)
- Enable "Install from Unknown Sources" if installing manually

**"Device not detected"**
- Check USB debugging is enabled
- Try different USB cable/port
- Install device drivers (Windows)
- Run `adb kill-server && adb start-server`

### Runtime Issues

**"Launcher keeps crashing"**
- Check logcat in Android Studio: **View** > **Tool Windows** > **Logcat**
- Look for error messages
- Try clearing app data: **Settings** > **Apps** > **CyKrome Launcher** > **Storage** > **Clear Data**

**"Can't set as default launcher"**
- Some manufacturers (Xiaomi, Huawei) may have additional security
- Go to **Settings** > **Apps** > **Manage Apps** > **Default Apps** > **Home App**

**"Notification badges not working"**
- Make sure Notification Access is granted
- Go to **Settings** > **Apps** > **Special Access** > **Notification Access**
- Enable **CyKrome Launcher**

## Debugging Tips

### View Logs
```bash
# View all logs
adb logcat

# Filter for launcher only
adb logcat | grep -i cykrome

# Clear logs and view new ones
adb logcat -c && adb logcat
```

### In Android Studio
1. Open **Logcat** tab (bottom of screen)
2. Filter by package: `com.cykrome.launcher`
3. Look for errors (red) or warnings (yellow)

### Common Log Filters
- `adb logcat *:E` - Show only errors
- `adb logcat *:W` - Show warnings and errors
- `adb logcat | grep -i "cykrome\|launcher"` - Filter launcher-related logs

## Building Release Version

For a release build (for distribution):

1. **Create a keystore:**
```bash
keytool -genkey -v -keystore cykrome-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias cykrome
```

2. **Create `keystore.properties` in project root:**
```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=cykrome
storeFile=cykrome-release-key.jks
```

3. **Update `app/build.gradle`:**
```gradle
android {
    ...
    signingConfigs {
        release {
            def keystorePropertiesFile = rootProject.file("keystore.properties")
            def keystoreProperties = new Properties()
            keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
            
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

4. **Build release:**
```bash
./gradlew assembleRelease
```

## Quick Test Checklist

- [ ] App installs successfully
- [ ] Can set as default launcher
- [ ] Home screen displays apps
- [ ] App drawer opens and shows all apps
- [ ] Can launch apps
- [ ] Settings menu accessible
- [ ] Grid size changes work
- [ ] Icon size changes work
- [ ] Gestures work (swipe up/down)
- [ ] Backup creates file
- [ ] Restore works (test with own backup)
- [ ] Hide apps works
- [ ] Notification badges work (if permission granted)

## Need Help?

- Check Android Studio's **Logcat** for error messages
- Review the **README.md** for feature documentation
- Check device compatibility (Android 8.0+ required)

---

**Note:** The first build may take 5-10 minutes as Gradle downloads dependencies. Subsequent builds will be much faster.

