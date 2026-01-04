# Mommy's Soles

An unofficial Android client for e621.net and e926.net image boards. This application provides a native mobile experience for browsing, searching, and interacting with e621/e926 content.

## Features

### Core Functionality
- Browse posts with infinite scroll pagination
- Advanced tag-based search with autocomplete suggestions
- View post details including tags, description, sources, and metadata
- Full-screen image and video viewing with zoom support
- Support for all media types: images, GIF animations, WebM videos

### User Account Features
- Login with e621 API key authentication
- View and manage favorites
- Vote on posts (upvote/downvote)
- View your posts, comments, and upload history
- Direct message (DMail) support
- Manage post sets

### Browsing Options
- Popular posts (daily, weekly, monthly)
- Browse tags, pools, artists, and users
- Pool navigation with sequential post viewing
- Random post discovery
- Saved searches for quick access

### Customization
- Safe/Questionable/Explicit content filtering
- Blacklist tag management
- Configurable grid layout (columns, thumbnail quality)
- Multiple host support (e621.net, e926.net, custom)
- Theme customization

### Additional Features
- Followed tags with background checking and notifications
- Download posts to device storage
- Share posts and images
- Deep link support for e621/e926 URLs
- PIN code lock for privacy
- Auto-update checker with in-app installation

## Recent Updates (v1.1.3)

### 🚀 New Features
- **Automatic Update Checker:** Daily background check for new versions using WorkManager. Shows notification when update is available.
- **Children Posts Dialog:** Complete redesign of children posts viewing. Shows all children (up to 320) with visual selection feedback.
- **Improved Downloads:** Fixed MediaStore integration for better file scanning and gallery visibility.
- **Dynamic Pagination:** Improved page count calculation based on actual post count.
- **Multi-tag Search Suggestions:** Better autocomplete when searching with multiple tags.

### 🐛 Bug Fixes
- **Children Display:** Fixed issue where only partial children were shown. Now fetches complete list via parent search.
- **Download Path:** Fixed file path display and scanning issues on Android 10+.
- **Pagination:** Fixed incorrect page count when browsing large result sets.

## Previous Updates (v1.1.0)

### 🚀 New Features
- **Instant Tag Monitoring:** Added a new "Instant (Beta)" option for followed tags. Checks for new posts every 30 seconds using a robust foreground service.
- **Custom Notification Sound:** New alerts for followed tags now use a custom notification sound.
- **Fullscreen Improvements:** 
  - Fixed image pixelation/blur when zooming in.
  - Added "Double Tap to Exit" gesture for both images and videos.

### 🐛 Bug Fixes
- **Biometric Unlock:** Fixed issue where the option appeared disabled on compatible devices. Updated biometric library for Android 14/15 support.
- **Update Checker:** Fixed "Failed to check updates" error by correcting the repository configuration.
- **UX Improvements:** Improved PIN setup flow when enabling biometrics.

## Requirements

- Android 7.0 (API 24) or higher
- Internet connection
- e621.net account (optional, required for favorites and voting)

## Installation

### From Release
1. Download the latest APK from the [Releases](https://github.com/AndoniXXR/mommy-s-soles/releases) page
2. Enable "Install from unknown sources" in your Android settings
3. Install the APK

### Building from Source

#### Prerequisites
- **JDK 17** or higher (recommended: Oracle JDK 17 or OpenJDK 17)
- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android SDK** with:
  - SDK Platform 34 (Android 14)
  - Build Tools 34.0.0
  - NDK (optional, for native libs)
- **Gradle 8.11.1** (bundled with project via wrapper)

#### Environment Setup

1. **Install JDK 17:**
   ```bash
   # Windows (using winget)
   winget install Oracle.JDK.17
   
   # macOS (using Homebrew)
   brew install openjdk@17
   
   # Linux (Ubuntu/Debian)
   sudo apt install openjdk-17-jdk
   ```

2. **Install Android Studio:**
   - Download from https://developer.android.com/studio
   - During setup, install Android SDK 34 and Build Tools

3. **Set Environment Variables:**
   ```bash
   # Windows (PowerShell)
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
   
   # macOS/Linux
   export ANDROID_HOME=$HOME/Android/Sdk
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
   ```

#### Build Steps

1. Clone this repository:
   ```bash
   git clone https://github.com/AndoniXXR/mommy-s-soles.git
   cd mommy-s-soles/E621Client
   ```

2. Build debug APK:
   ```bash
   # Windows
   .\gradlew.bat assembleDebug
   
   # macOS/Linux
   ./gradlew assembleDebug
   ```

3. The APK will be generated at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

4. Install on device/emulator:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

#### Build Variants
- `debug` - Development build with debugging enabled
- `release` - Production build (requires signing configuration)

## Configuration

### API Authentication
To use account features, you need an e621 API key:
1. Log in to e621.net
2. Go to Account > Manage API Access
3. Create a new API key
4. Use your username and API key to log in within the app

### Host Selection
The app supports multiple hosts:
- **e621.net** - Full content (default)
- **e926.net** - Safe content only
- **Custom** - Self-hosted instances

## Project Structure

```
app/
├── src/main/
│   ├── java/com/e621/client/
│   │   ├── data/
│   │   │   ├── api/          # API client and network layer
│   │   │   ├── model/        # Data models (Post, Tag, Pool, etc.)
│   │   │   ├── preferences/  # SharedPreferences management
│   │   │   └── search/       # Search suggestions provider
│   │   ├── ui/
│   │   │   ├── adapter/      # RecyclerView adapters
│   │   │   ├── auth/         # Login activity
│   │   │   ├── browse/       # Browse tags, pools, users
│   │   │   ├── comments/     # Comments viewing
│   │   │   ├── dmail/        # Direct messages
│   │   │   ├── pools/        # Pool viewing
│   │   │   ├── popular/      # Popular posts
│   │   │   ├── post/         # Post detail and fullscreen
│   │   │   ├── profile/      # User profile
│   │   │   ├── saved/        # Saved searches
│   │   │   ├── sets/         # Post sets
│   │   │   ├── settings/     # App settings
│   │   │   └── wiki/         # Wiki pages
│   │   ├── util/             # Utility classes
│   │   └── worker/           # Background workers
│   └── res/                  # Resources (layouts, drawables, values)
└── build.gradle.kts          # Build configuration
```

## Tech Stack

- **Language**: Kotlin 2.0.21
- **Min SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **UI**: Android Views + Data Binding
- **Networking**: OkHttp 4.12.0 + Retrofit 2.9.0
- **Image Loading**: Coil 2.5.0
- **Video Playback**: ExoPlayer (Media3) 1.5.1
- **Background Tasks**: WorkManager 2.9.0
- **Authentication**: Biometric API 1.4.0-alpha02
- **Architecture**: MVVM-inspired with Kotlin Coroutines

### Key Dependencies
```kotlin
// Networking
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")

// Image Loading
implementation("io.coil-kt:coil:2.5.0")
implementation("io.coil-kt:coil-gif:2.5.0")
implementation("io.coil-kt:coil-video:2.5.0")

// Video Playback
implementation("androidx.media3:media3-exoplayer:1.5.1")
implementation("androidx.media3:media3-ui:1.5.1")

// Background Work
implementation("androidx.work:work-runtime-ktx:2.9.0")

// UI Components
implementation("com.google.android.material:material:1.11.0")
implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
implementation("com.github.chrisbanes:PhotoView:2.3.0")
```

## API Reference

This app uses the e621 API v1. Documentation available at:
- https://e621.net/wiki_pages/2425

## Updates

The app includes an automatic update checker that fetches the latest release from this GitHub repository. When a new version is available, you will be prompted to download and install it.

## License

This project is provided as-is for educational purposes. e621 is a registered trademark of Bad Dragon.

## Disclaimer

This is an unofficial client and is not affiliated with or endorsed by e621.net. Use at your own risk and in accordance with e621's Terms of Service.

## Contributing

Contributions are welcome. Please open an issue or submit a pull request.

## Version History

- **1.1.3** - Children posts & auto-updates
  - Automatic daily update checking with notifications
  - Complete children posts dialog with visual feedback
  - Fixed downloads and MediaStore integration
  - Improved pagination and search suggestions

- **1.1.0** - Instant monitoring & UX improvements
  - Instant tag monitoring (30 second intervals)
  - Custom notification sounds
  - Fullscreen zoom and double-tap gestures
  - Biometric unlock fixes

- **1.0.0** - Initial release
  - Full browsing and search functionality
  - User account integration
  - Media viewing (images, GIFs, videos)
  - Favorites and voting
  - Pools and sets support
  - Blacklist and content filtering
  - Background tag following with notifications
  - Auto-update system
