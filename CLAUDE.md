# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A JavaFX desktop application for managing and previewing osu! skins. Built with Java 17, JavaFX 19, and Maven for cross-platform skin management with features like smart path detection, metadata extraction, and preview functionality.

## Development Commands

### Core Build Commands
- **Compile**: `mvn clean compile`
- **Run Application**: `mvn javafx:run`
- **Run Tests**: `mvn test`
- **Build JAR**: `mvn clean package` (creates shaded JAR in target/)
- **Native Package**: `mvn clean package -P jpackage` (Windows installer)

### Testing Commands
- **All Tests**: `mvn test`
- **Single Test Class**: `mvn test -Dtest=ClassName`
- **TestFX GUI Tests**: `mvn test -Dtest=*Test` (uses TestFX framework)

## Architecture Overview

### Core Design Patterns
- **MVC Architecture**: Clear separation with controllers, models, and FXML views
- **Service Layer**: Business logic isolated in service classes
- **Dependency Injection**: Manual DI through constructor injection and setters
- **Configuration Management**: JSON-based persistence with Jackson
- **Asynchronous Operations**: JavaFX Tasks for non-blocking UI operations

### Key Components

**Models** (`com.osuskin.tool.model`):
- `Skin`: Rich domain model with element management, metadata, and JSON serialization
- `SkinElement`: Represents individual skin files with type classification
- `SkinElementRegistry`: Comprehensive catalog of all osu! skin elements with categories
- `Configuration`: Application settings and user preferences

**Services** (`com.osuskin.tool.service`):
- `SkinScannerService`: Directory scanning with async Task support and metadata extraction
- `SkinElementLoader`: Flexible element loading with fallback to default skin
- `ConfigurationService`: Settings management and persistence

**Controllers** (`com.osuskin.tool.controller`):
- `MainController`: Primary UI controller with embedded preview functionality
- `SkinPreviewController`: Standalone preview window controller (deprecated)

**View Components** (`com.osuskin.tool.view`):
- `SimpleGameplayRenderer`: Basic animated skin preview renderer
- `GameplayRenderer`: Enhanced renderer with full gameplay simulation
- `gameplay/HitObject`: Base class for preview hit objects
- `gameplay/HitCircle`: Circle implementation with timing
- `gameplay/Slider`: Slider implementation with ball animation
- `gameplay/HitBurst`: Hit burst animation manager
- `gameplay/GameplayUI`: UI overlay system (health, score, combo)

**Utilities** (`com.osuskin.tool.util`):
- `ConfigurationManager`: JSON config file I/O with Jackson
- `OsuPathDetector`: Cross-platform osu! installation detection

### Data Flow Architecture
1. **Initialization**: ConfigurationManager loads settings → OsuPathDetector finds default paths
2. **Scanning**: SkinScannerService.createScanTask() → Async directory traversal → Skin model population
3. **UI Updates**: Controller receives Task updates → Updates JavaFX components
4. **Persistence**: Configuration changes → ConfigurationManager.saveConfiguration()

## Technology Stack

### Core Dependencies
- **Java 17**: Language version (required)
- **JavaFX 19**: UI framework (controls, FXML, media)
- **Jackson 2.15**: JSON serialization/deserialization
- **SLF4J + Logback**: Logging framework
- **Apache Commons IO**: File operations
- **JUnit 5 + TestFX**: Testing framework

### Build Tools
- **Maven**: Build automation with shade plugin for fat JARs
- **JavaFX Maven Plugin**: Development server and packaging
- **JPackage**: Native installer creation

## File Structure Patterns

### Resource Organization
```
src/main/resources/
├── fxml/              # JavaFX FXML layout files
│   ├── main.fxml      # Main window with embedded preview
│   └── skin-preview.fxml # Standalone preview (deprecated)
├── css/               # Application stylesheets
│   └── application.css # Main stylesheet
├── default-skin/      # Default osu! skin fallback elements
│   ├── *.png          # Default skin PNG files
│   └── README.md      # Setup instructions
├── icons/             # Application icons
└── logback.xml        # Logging configuration
```

### Package Structure
- `controller/`: UI controllers (typically paired with FXML files)
- `model/`: Domain objects with JSON annotations
- `service/`: Business logic and external operations
- `util/`: Helper classes and utilities
- `view/`: Custom JavaFX components (future expansion)

## Configuration System

### Configuration Storage
- **Path**: Platform-specific app data directory
- **Format**: JSON via Jackson with JSR-310 datetime support
- **Auto-save**: On application shutdown and major operations

### Key Configuration Properties
- `osuSkinsDirectory`: Target directory for skin scanning
- `lastScanTime`: Caching optimization
- `thumbnailSize`: UI preference
- `autoScan`: Startup behavior

## Skin Processing Pipeline

### Discovery Process
1. **Directory Validation**: Check for skin.ini or known skin files
2. **Metadata Parsing**: Extract name, author, version from skin.ini
3. **Element Scanning**: Map files to SkinElement.ElementType enum
4. **Statistics**: File count, total size, last modified
5. **Preview Detection**: Find suitable preview images

### Element Type System
- Enum-based classification of skin files (defined in `SkinElement.ElementType`)
- Automatic file-to-type mapping via filename patterns
- Support for images (.png, .jpg) and audio (.wav, .mp3, .ogg)

### Compressed File Extraction
- **Nested Folder Detection**: Analyzes ZIP structure to identify single root folders
- **Smart Stripping**: Removes container folders when >80% of skin files are nested
- **Skin File Recognition**: Detects skin.ini, hitcircle, cursor, slider elements
- **Structure Preservation**: Maintains subdirectories while flattening unnecessary nesting

## Development Guidelines

### Code Conventions
- Follow JavaFX naming patterns for FXML controllers
- Use SLF4J for logging (logger fields: `private static final Logger logger`)
- Jackson annotations for JSON serialization
- Path objects preferred over String paths
- Builder pattern not used - prefer constructors and setters

### Error Handling
- IOException for file operations
- Runtime exceptions for configuration errors  
- Graceful degradation for missing skin elements
- User-friendly error dialogs in JavaFX controllers

### Testing Strategy
- TestFX for GUI testing (configured in pom.xml)
- JUnit 5 for unit tests
- Mock skin directories for testing
- No current test files exist - create as needed

## Platform-Specific Notes

### Default osu! Paths
- **Windows**: `%LOCALAPPDATA%\osu!\Skins\`
- **macOS**: `~/Library/Application Support/osu!/Skins/`  
- **Linux**: `~/.local/share/osu!/Skins/`

### JavaFX Packaging
- Fat JAR includes all dependencies via Maven Shade plugin
- JPackage profile for native installers (Windows tested)
- Cross-platform compatibility verified

## Current Implementation Status

### ✅ Completed Features
- **Core Application Structure**: JavaFX setup, Maven configuration, cross-platform support
- **Configuration System**: JSON persistence, auto-save, platform-specific paths
- **Directory Management**: Smart osu! path detection, directory validation
- **Skin Scanning**: Metadata extraction, element counting, skin.ini parsing, combo color parsing
- **Compressed File Support**: ZIP/OSK extraction with nested folder handling
- **UI Implementation**: Clean interface with simplified controls
- **Embedded Preview System**:
  - Audio preview with volume control
  - Hitsounds and misc sounds playback
  - Visual animation with actual skin elements
  - Automatic continuous playback
  - Proper element scaling and layering
- **Default Skin Fallback**: Resources folder with default elements
- **Enhanced Gameplay Preview**:
  - Hit burst animations (50/100/300/miss) with sprite support
  - Dynamic lighting effects on hits
  - Full slider implementation with ball animation and reverse arrows
  - Smooth cursor movement with trail effects
  - Health bar with danger indicators
  - Score and combo counters with skin fonts
  - Combo color system from skin.ini
  - Varied hit results for realistic gameplay
  - Proper hit object timeline with overlap
  - Consistent scaling system for all elements

### ⏳ Planned Features
- Export/import functionality
- Skin editing capabilities
- Batch operations
- Cloud sync support

## Related Documentation
- `OSU_SKIN_RENDERING.md` - Comprehensive guide to osu! skin element rendering
- `src/main/resources/default-skin/README.md` - Default skin setup instructions
- `src/main/resources/default-skin/REQUIRED_ELEMENTS.md` - Complete element list