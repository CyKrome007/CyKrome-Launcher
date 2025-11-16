# CyKrome Launcher

A feature-rich Android launcher with Nova Launcher Prime-like functionality, built with Kotlin and modern Android development practices.

## Features

### Home Screen Customization
- **Customizable Grid Size**: Adjust columns and rows (3-8 columns, 3-10 rows)
- **Icon Size Control**: Resize icons from 50% to 150%
- **Icon Labels**: Toggle app labels on/off
- **Desktop Padding**: Adjust spacing between icons
- **Multiple Home Screens**: Swipe between multiple desktop pages
- **Scroll Effects**: Choose from Cube, Cylinder, Carousel, or None

### App Drawer
- **Multiple Styles**: Vertical, Horizontal, or List view
- **Tabbed Organization**: Apps organized by first letter with tabs
- **Custom Grid Size**: Adjustable drawer grid layout
- **Hide Apps**: Hide apps from drawer without uninstalling
- **Search Functionality**: Quick search through all installed apps

### Dock
- **Scrollable Dock**: Enable horizontal scrolling for more dock icons
- **Customizable Size**: Adjust number of dock icons (up to 7)
- **Persistent Dock**: Dock stays visible across all home screens

### Gestures
- **Swipe Up**: Customizable action (App Drawer, Search, Notifications, etc.)
- **Swipe Down**: Customizable action
- **Double Tap**: Customizable action
- **Pinch Gestures**: Pinch in/out actions (to be expanded)

### Appearance
- **Icon Pack Support**: Use custom icon packs from Play Store
- **Scroll Effects**: Multiple page transition animations
- **Animation Speed**: Adjust animation speed (0.5x to 2.0x)
- **Theme Support**: Material Design with dark/light theme support

### Notification Badges
- **Unread Counts**: Display notification counts on app icons
- **Badge Visibility**: Toggle badges on/off
- **Real-time Updates**: Badges update automatically

### Backup & Restore
- **Settings Backup**: Export launcher settings to JSON file
- **Settings Restore**: Import settings from backup file
- **Nova Launcher Compatibility**: Restore settings from Nova Launcher backup files (.novabackup)
- **Easy Migration**: Transfer settings between devices or from Nova Launcher

### Additional Features
- **Widget Support**: Full Android widget support
- **Folder Support**: Create and customize app folders
- **Long Press Actions**: Long press for app options
- **Boot Receiver**: Launcher starts automatically on device boot

## Requirements

- Android 8.0 (API 26) or higher
- Android Studio Arctic Fox or later
- Kotlin 1.9.20+
- Gradle 8.1.2+

## Installation

### Quick Start

1. **Clone the repository:**
```bash
git clone https://github.com/yourusername/CyKrome-Launcher.git
cd CyKrome-Launcher
```

2. **Open in Android Studio:**
   - Launch Android Studio
   - File > Open > Select the `CyKrome-Launcher` folder
   - Wait for Gradle sync to complete

3. **Connect your Android device:**
   - Enable USB Debugging (Settings > Developer Options)
   - Connect via USB

4. **Build and Install:**
   - Click the green **Run** button in Android Studio
   - Or press `Shift+F10` (Windows/Linux) or `Ctrl+R` (Mac)
   - The app will build and install automatically

5. **Set as Default Launcher:**
   - When prompted, select **CyKrome Launcher** and tap **Always**

**For detailed instructions, see [BUILD_AND_TEST.md](BUILD_AND_TEST.md)**

## Setup

### Setting as Default Launcher
1. After installation, go to **Settings > Apps > Default Apps > Home App**
2. Select **CyKrome Launcher**

### Enabling Notification Badges
1. Go to **Settings > Apps > Special Access > Notification Access**
2. Enable **CyKrome Launcher**

## Project Structure

```
app/
├── src/main/
│   ├── java/com/cykrome/launcher/
│   │   ├── data/              # Preferences and data models
│   │   ├── model/             # Data models (AppInfo, etc.)
│   │   ├── ui/                # UI components
│   │   │   ├── fragments/     # HomeScreen, AppDrawer, Search
│   │   │   ├── settings/      # Settings activities
│   │   │   └── adapters/      # RecyclerView adapters
│   │   ├── util/              # Utilities (AppLoader, BadgeHelper, etc.)
│   │   ├── widget/            # App widget provider
│   │   └── receiver/          # Broadcast receivers
│   └── res/                   # Resources (layouts, drawables, etc.)
```

## Key Components

### LauncherActivity
Main activity that handles launcher lifecycle, gestures, and navigation between home screen, app drawer, and search.

### HomeScreenFragment
Manages the home screen with multiple pages, desktop grid, and app icons.

### AppDrawerFragment
Handles the app drawer with tabs, search, and app organization.

### LauncherPreferences
Centralized preference management using SharedPreferences.

### AppLoader
Asynchronously loads installed apps with support for icon packs.

### BadgeHelper
Manages notification badges and unread counts.

## Customization Guide

### Changing Grid Size
1. Open Settings
2. Navigate to **Home Screen**
3. Adjust **Grid Size** sliders for columns and rows

### Hiding Apps
1. Open Settings
2. Tap **Hide Apps**
3. Check apps you want to hide from the drawer

### Setting Up Gestures
1. Open Settings
2. Navigate to **Gestures**
3. Configure swipe up, swipe down, and double tap actions

### Using Icon Packs
1. Install an icon pack from Play Store
2. Open Settings > Appearance
3. Select your icon pack

### Restoring from Nova Launcher
1. Export your backup from Nova Launcher (Settings > Backup & Restore)
2. Open CyKrome Launcher Settings
3. Tap **Restore Settings**
4. Select your Nova Launcher backup file (.novabackup)
5. The launcher will automatically detect and convert Nova settings
6. Restart the launcher for changes to take effect

**Supported Nova Settings:**
- Grid size (columns and rows)
- Icon size and labels
- App drawer style
- Hidden apps
- Dock settings
- Gesture actions
- Icon pack
- Scroll effects
- Animation speed
- Notification badges

## Development

### Building from Source

```bash
./gradlew assembleDebug
```

### Running Tests

```bash
./gradlew test
```

### Generating APK

```bash
./gradlew assembleRelease
```

## Permissions

The launcher requires the following permissions:
- `QUERY_ALL_PACKAGES`: To list all installed apps
- `BIND_NOTIFICATION_LISTENER_SERVICE`: For notification badges
- `BIND_APPWIDGET`: For widget support
- `RECEIVE_BOOT_COMPLETED`: To start on boot

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Inspired by Nova Launcher Prime
- Built with Material Design components
- Uses modern Android architecture patterns

## Roadmap

- [ ] Advanced folder customization
- [ ] More gesture options
- [ ] Desktop widget management UI
- [ ] Advanced animations
- [ ] Wallpaper picker integration
- [ ] Backup to cloud services
- [ ] More icon pack formats
- [ ] Performance optimizations

## Support

For issues, feature requests, or questions, please open an issue on GitHub.

---

**Note**: This is a comprehensive launcher implementation. Some advanced features may require additional development and testing on various Android devices.
