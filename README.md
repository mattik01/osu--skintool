# osu! Skin Selection Tool

A comprehensive JavaFX application for managing, previewing, and organizing osu! skins with advanced features like caching, metadata extraction, and a dedicated "Skin Container" system.

## Features

### Core Functionality
- **Directory Management**: Select and scan your osu!/skins folder
- **Skin Caching**: Fast startup with cached skin metadata and thumbnails
- **Skin Container**: Non-destructive copying of skin elements to a managed container
- **Preview System**: View skin images and play audio elements
- **Search & Filter**: Find skins by name, author, or tags
- **Favorites**: Mark and filter favorite skins

### Technical Features
- **Asynchronous Scanning**: Responsive UI during skin directory scanning
- **Metadata Extraction**: Automatic parsing of skin.ini files
- **Thumbnail Generation**: Cached previews for fast browsing
- **Configuration Management**: Persistent settings and preferences
- **Cross-platform**: Works on Windows, macOS, and Linux

## Requirements

- **Java 17 or later**
- **Apache Maven 3.6+**
- **JavaFX 19+** (included in dependencies)

## Installation & Setup

### 1. Install Prerequisites

#### Windows
1. Download and install [Java 17+ JDK](https://adoptium.net/)
2. Download and install [Apache Maven](https://maven.apache.org/download.cgi)
3. Ensure both `java` and `mvn` are in your system PATH

#### macOS
```bash
# Using Homebrew
brew install openjdk@17 maven
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-17-jdk maven
```

### 2. Build and Run

#### Quick Start (Windows)
```cmd
# Double-click run.bat or execute in command prompt
run.bat
```

#### Manual Build
```bash
# Compile the project
mvn clean compile

# Run the application
mvn javafx:run

# Or create a runnable JAR
mvn clean package
java -jar target/osu-skintool-1.0.0-shaded.jar
```

### 3. Create Windows Executable (Optional)
```bash
# Build with jpackage profile (requires JDK 17+)
mvn clean package -P jpackage
```
The executable will be created in `target/dist/`

## Usage

### First Run
1. Launch the application
2. Select your osu!/skins directory when prompted
3. Wait for the initial scan to complete
4. Browse your skins in the main window

### Main Interface
- **Left Panel**: List of all discovered skins
- **Right Panel**: Detailed information about selected skin
- **Search Bar**: Filter skins by name or author
- **Favorites Button**: Toggle display of favorite skins only
- **Menu Bar**: Access settings, import/export, and tools

### Skin Container
The application creates a "Skin Container" folder in your skins directory where you can copy individual skin elements without modifying the original skins.

## Configuration

Settings are automatically saved to:
- **Windows**: `%APPDATA%/OsuSkinTool/config.json`
- **macOS**: `~/Library/Application Support/OsuSkinTool/config.json`
- **Linux**: `~/.config/OsuSkinTool/config.json`

### Configuration Options
- **Thumbnail Size**: Preview image size (50-300px)
- **Audio Preview**: Enable/disable audio playback
- **Auto Scan**: Automatically scan directory on startup
- **Cache Directory**: Location for cached data

## Project Structure

```
osu-skintool/
├── src/main/java/com/osuskin/tool/
│   ├── OsuSkinToolApplication.java     # Main application class
│   ├── controller/                     # UI controllers
│   │   └── MainController.java
│   ├── model/                         # Data models
│   │   ├── Configuration.java
│   │   ├── Skin.java
│   │   └── SkinElement.java
│   ├── service/                       # Business logic
│   │   ├── ConfigurationService.java
│   │   └── SkinScannerService.java
│   ├── util/                          # Utilities
│   │   └── ConfigurationManager.java
│   └── view/                          # View components
├── src/main/resources/
│   ├── fxml/                          # JavaFX layouts
│   │   └── main.fxml
│   ├── css/                           # Stylesheets
│   │   └── application.css
│   └── icons/                         # Application icons
├── pom.xml                            # Maven configuration
└── README.md                          # This file
```

## Development

### Architecture
- **MVC Pattern**: Clean separation of concerns
- **Service Layer**: Business logic and data processing
- **Asynchronous Operations**: Non-blocking UI operations
- **Configuration Management**: JSON-based settings persistence

### Key Components
- **SkinScannerService**: Handles directory scanning and metadata extraction
- **ConfigurationManager**: Manages application settings and persistence
- **MainController**: Primary UI controller and event handling

### Building for Distribution
```bash
# Build fat JAR with all dependencies
mvn clean package

# Build Windows installer (requires Windows + WiX Toolset)
mvn clean package -P jpackage
```

## Troubleshooting

### Common Issues

**Application won't start**
- Verify Java 17+ is installed: `java -version`
- Check JavaFX modules are available
- Review logs in the configuration directory

**Skin scan fails**
- Ensure osu! directory is accessible
- Check file permissions on skin folders
- Review application logs for specific errors

**Performance issues**
- Clear application cache (Tools > Clear Cache)
- Reduce thumbnail size in settings
- Disable audio preview if not needed

### Logging
Application logs are saved to:
- **Windows**: `%APPDATA%/OsuSkinTool/logs/`
- **macOS**: `~/Library/Application Support/OsuSkinTool/logs/`
- **Linux**: `~/.config/OsuSkinTool/logs/`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## Future Features (Phase 2)

- **Randomization Groups**: Create groups for random skin element selection
- **Batch Operations**: Copy multiple elements at once
- **Skin Themes**: Save and load skin element combinations
- **Desktop Integration**: Quick randomization shortcuts
- **Plugin System**: Extensible architecture for custom features

## License

This project is open source and available under the [MIT License](LICENSE).

## Acknowledgments

- Built for the osu! community
- Uses JavaFX for modern UI
- Inspired by the need for better skin management tools

---

**Note**: This application is not affiliated with osu! or ppy Pty Ltd. osu! is a trademark of ppy Pty Ltd.