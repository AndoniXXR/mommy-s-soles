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

## Recent Updates (v1.1.0)

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
1. Clone this repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Build and run on your device or emulator

```bash
git clone https://github.com/AndoniXXR/mommy-s-soles.git
cd mommy-s-soles
./gradlew assembleDebug
```

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

- **Language**: Kotlin
- **UI**: Android Views + Jetpack Compose (hybrid)
- **Networking**: OkHttp + Retrofit
- **Image Loading**: Coil
- **Video Playback**: ExoPlayer (Media3)
- **Background Tasks**: WorkManager
- **Architecture**: MVVM-inspired with coroutines

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

- **1.0.0** - Initial release
  - Full browsing and search functionality
  - User account integration
  - Media viewing (images, GIFs, videos)
  - Favorites and voting
  - Pools and sets support
  - Blacklist and content filtering
  - Background tag following with notifications
  - Auto-update system
