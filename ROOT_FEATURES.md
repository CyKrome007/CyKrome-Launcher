# Root Features for CyKrome Launcher

The launcher now includes enhanced support for rooted Android devices. These features are **optional** - the launcher works perfectly fine on non-rooted devices, but can take advantage of root access when available.

## Root Detection

The launcher automatically detects if your device is rooted using multiple methods:
- Checks for common root binaries (`/system/bin/su`, `/system/xbin/su`, etc.)
- Checks for root management apps
- Verifies root access availability

## Root Features

### 1. Automatic Permission Granting

**What it does:**
- Automatically grants `QUERY_ALL_PACKAGES` permission on startup if root is available
- Allows the launcher to see all installed apps, even system apps
- Works around Android 11+ restrictions

**How it works:**
- On launcher startup, if root is detected, it attempts to grant permissions via `pm grant` command
- No user interaction required (if root access is already granted to the app)

### 2. Enhanced App Discovery

**What it does:**
- Uses `MATCH_ALL` flag when querying apps if root is available
- Shows all apps including system apps and hidden apps
- Better app list completeness

**How it works:**
- If `QUERY_ALL_PACKAGES` permission is not granted normally, root is used to grant it
- Falls back to `MATCH_DEFAULT_ONLY` if root grant fails

### 3. Root Settings Panel

**Location:** Settings → Root Features

**Features:**
- **Root Status**: Shows if device is rooted and if root access is available
- **Grant Permissions**: Manually grant all permissions using root (useful if auto-grant failed)

## How to Use

### For Rooted Users

1. **First Time Setup:**
   - Install the launcher normally
   - Grant root access when prompted by your root manager (SuperSU, Magisk, etc.)
   - The launcher will automatically grant permissions on first launch

2. **Manual Permission Grant:**
   - If automatic grant didn't work, go to Settings → Root Features
   - Tap "Grant Permissions (Root)"
   - Confirm root access in your root manager

3. **Verify Root Status:**
   - Go to Settings → Root Features
   - Check "Root Status" - should show "Root access available ✓"

### For Non-Rooted Users

- The launcher works normally without root
- Root features section is hidden automatically
- All standard features work as expected

## Technical Details

### Root Detection Methods

1. **File System Check**: Looks for common root binaries in standard locations
2. **Runtime Check**: Attempts to execute `su` command
3. **Build Tags Check**: Checks for test-keys in build tags

### Root Commands Used

- `pm grant <package> <permission>` - Grants runtime permissions
- `su` - Checks for root access availability

### Security Notes

- Root access is only used for permission granting
- No sensitive data is accessed via root
- All root operations are logged for debugging
- Root features are completely optional

## Troubleshooting

### Root Not Detected

**Possible causes:**
- Root manager not properly installed
- SELinux restrictions
- Root binary in non-standard location

**Solutions:**
- Ensure root manager (Magisk/SuperSU) is working
- Check if other root apps work
- Try manually granting root to the launcher

### Root Access Denied

**Possible causes:**
- Root manager denied access
- Root access not granted to launcher

**Solutions:**
- Check root manager logs
- Manually grant root access to CyKrome Launcher
- Use "Grant Permissions" button in settings

### Permissions Still Not Granted

**Possible causes:**
- Root command failed
- Permission already granted but not recognized
- Android version restrictions

**Solutions:**
- Check logcat for error messages: `adb logcat | grep RootHelper`
- Try manual grant from settings
- Restart the launcher after granting

## Benefits for Rooted Users

1. **Better App Discovery**: See all apps including system apps
2. **No Permission Prompts**: Permissions granted automatically via root
3. **Enhanced Functionality**: Full access to all launcher features
4. **System App Support**: Can interact with system apps if needed

## Privacy & Security

- Root access is only requested when needed
- No data is sent anywhere
- All operations are local
- Root features can be disabled by denying root access

## Compatibility

- **Works with:** Magisk, SuperSU, KingRoot, and most root solutions
- **Android Versions:** Android 8.0+ (API 26+)
- **Tested on:** Android 11, 12, 13, 14

---

**Note:** Root features are completely optional. The launcher works perfectly on non-rooted devices. Root access only enhances functionality when available.

