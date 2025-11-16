# Project Structure

## Overview
This Android launcher project is organized following modern Android development best practices with a clear separation of concerns.

## Directory Structure

```
CyKrome-Launcher/
├── app/
│   ├── build.gradle                    # App-level build configuration
│   ├── proguard-rules.pro              # ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml         # App manifest with permissions and components
│       ├── java/com/cykrome/launcher/
│       │   ├── LauncherApplication.kt  # Application class
│       │   ├── data/
│       │   │   └── LauncherPreferences.kt  # SharedPreferences wrapper
│       │   ├── model/
│       │   │   └── AppInfo.kt          # App data model
│       │   ├── ui/
│       │   │   ├── LauncherActivity.kt  # Main launcher activity
│       │   │   ├── fragments/          # UI fragments
│       │   │   │   ├── HomeScreenFragment.kt
│       │   │   │   ├── AppDrawerFragment.kt
│       │   │   │   └── SearchFragment.kt
│       │   │   ├── settings/           # Settings activities
│       │   │   │   ├── SettingsActivity.kt
│       │   │   │   └── HideAppsActivity.kt
│       │   │   └── adapters/           # RecyclerView adapters
│       │   │       ├── AppIconAdapter.kt
│       │   │       └── HideAppsAdapter.kt
│       │   ├── util/                   # Utility classes
│       │   │   ├── AppLoader.kt        # Loads installed apps
│       │   │   ├── BadgeHelper.kt      # Notification badge management
│       │   │   ├── NotificationListener.kt  # Notification listener service
│       │   │   └── BackupRestoreHelper.kt   # Backup/restore functionality
│       │   ├── widget/
│       │   │   └── AppWidgetProvider.kt  # App widget provider
│       │   └── receiver/
│       │       └── BootReceiver.kt     # Boot receiver
│       └── res/
│           ├── layout/                 # XML layouts
│           ├── values/                 # Resources (strings, colors, themes, arrays)
│           ├── xml/                    # Preference screens, file paths
│           └── drawable/               # Drawable resources
├── build.gradle                        # Project-level build configuration
├── settings.gradle                     # Gradle settings
├── gradle.properties                   # Gradle properties
├── README.md                           # Project documentation
└── .gitignore                          # Git ignore rules
```

## Key Components

### Core Components
- **LauncherActivity**: Main entry point, handles gestures and navigation
- **HomeScreenFragment**: Manages home screen pages and desktop
- **AppDrawerFragment**: Handles app drawer with tabs
- **SearchFragment**: Provides app search functionality

### Data Layer
- **LauncherPreferences**: Centralized preference management
- **AppInfo**: Data model for installed apps
- **AppLoader**: Asynchronously loads apps with icon pack support

### UI Components
- **AppIconAdapter**: Displays app icons in grid/list
- **HideAppsAdapter**: Manages hidden apps list
- **SettingsActivity**: Comprehensive settings UI

### Services & Receivers
- **NotificationListener**: Tracks notifications for badges
- **BootReceiver**: Handles device boot events
- **AppWidgetProvider**: Manages app widgets

## Architecture Patterns

1. **MVVM-like Structure**: Separation of UI, data, and business logic
2. **Repository Pattern**: AppLoader acts as repository for apps
3. **Observer Pattern**: LiveData for badge updates
4. **Singleton Pattern**: Application class and preference manager

## Dependencies

- **AndroidX Libraries**: Core, AppCompat, Material, RecyclerView, ViewPager2
- **Lifecycle Components**: For lifecycle-aware components
- **Coroutines**: For asynchronous operations
- **Gson**: For JSON serialization (backup/restore)
- **Preference**: For settings UI

## Build Configuration

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Kotlin**: 1.9.20
- **Gradle**: 8.1.2

## Features Implementation Status

✅ Home screen customization
✅ App drawer with tabs
✅ Gesture controls
✅ Notification badges
✅ Icon pack support
✅ Settings UI
✅ Hide apps
✅ Backup/restore (basic)
✅ Scroll effects
✅ Search functionality

## Next Steps for Enhancement

- Advanced folder customization UI
- Widget management UI
- Cloud backup integration
- More animation options
- Performance optimizations
- Unit tests
- UI tests

