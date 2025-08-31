# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Project Overview

A high-performance JavaFX application for managing and previewing osu! skins with real-time gameplay simulation and advanced caching.

## Development Commands

### Build & Run
- **Compile**: `mvn clean compile`
- **Run**: `mvn javafx:run`
- **Package JAR**: `mvn clean package`
- **Run Tests**: `mvn test`

## Architecture

### Active Services
- **LazyLoadingSkinService**: Primary skin loading with lazy initialization
- **SkinContainerService**: Manages skin containers and extraction
- **AsyncPreviewLoader**: Asynchronous preview loading with thread pool (4 threads default)
- **AudioMixerService**: Audio preview and hitsound playback
- **ManifestCache**: Caches skin element manifests
- **DefaultSkinCache**: Singleton cache for default skin elements
- **SkinElementLoader**: Loads individual elements with fallback
- **PerformanceMonitor**: Tracks performance metrics

### Core Models
- **Skin**: Domain model with metadata and element management
- **SkinContainer**: Manages extracted skin containers
- **SkinElement**: Individual skin file representation
- **Configuration**: Application settings persistence
- **PreviewElements**: Elements needed for preview
- **ElementGroup**: Priority-based element grouping

### Controller
- **MainController**: Single UI controller with embedded preview

### View Components
- **GameplayRenderer**: Full gameplay preview with animations
- **GameplayUI**: Overlay UI (health, score, combo)
- **HitCircle/Slider**: Hit object implementations
- **SharedElementCache**: Shared cache for gameplay elements

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

### Testing
- JUnit 5 for unit tests
- TestFX for GUI testing (if needed)
- Mock skin directories for testing

## Platform-Specific Notes

### Default osu! Paths
- **Windows**: `%LOCALAPPDATA%\osu!\Skins\`
- **macOS**: `~/Library/Application Support/osu!/Skins/`  
- **Linux**: `~/.local/share/osu!/Skins/`


## Performance Optimization Guidelines

### Key Principles
- **Async Everything**: All skin loading must be asynchronous to prevent UI freezes
- **Manifest-Based Loading**: Pre-scan directories to avoid checking non-existent files
- **Selective Loading**: Only load elements that exist and are needed for current view
- **Resource Reuse**: Pre-initialize renderers at startup, reuse between skin switches
- **Progressive Updates**: Fine-grained progress reporting for smooth UI feedback

### Critical Performance Areas

#### 1. Skin Loading
- Use `AsyncPreviewLoader` for all skin loading operations
- Implement `SkinElementManifest` to track available elements
- Never perform I/O operations on the UI thread
- Clear previous preview immediately on skin change

#### 2. File System Operations
- Build element manifest with single directory scan
- Cache file existence checks
- Skip attempts to load non-existent elements
- Use parallel loading for independent element groups

#### 3. Animation Optimization
- Smart scorebar-colour frame sampling: load maximum 10 frames regardless of total count
- Use exponential/binary search to efficiently find frame range (0 to N)
- Sample frames evenly across animation (if >10 frames, load at intervals)
- 95% reduction for skins with many frames (10 frames instead of 200+)
- Implement frame skipping for other high frame-count animations
- Use weak references for non-critical frames

#### 4. Memory Management
- Implement LRU cache for skin elements
- Release resources immediately on skin switch
- Downsample large images for preview if needed
- Clear caches when switching skins

#### 5. Progress Reporting
- Update progress per element, not per batch
- Use atomic counters for thread-safe progress
- Smooth progress bar updates with Platform.runLater()
- Show meaningful status messages during loading

### Performance Targets
- Skin load time: < 200ms for average skin
- Zero UI freezes during loading
- 80% reduction in file system operations
- Smooth 60 FPS progress updates
- Instant preview clearing on skin change

### Anti-Patterns to Avoid
- ❌ Loading all possible elements blindly
- ❌ Synchronous I/O on UI thread
- ❌ Creating new renderers for each skin
- ❌ Loading all animation frames
- ❌ Checking for non-existent files repeatedly

## Related Documentation
- `docs/ARCHITECTURE.md` - System architecture details
- `docs/OSU_SKIN_RENDERING.md` - osu! skin element specifications
- `src/main/resources/default-skin/README.md` - Default skin setup instructions