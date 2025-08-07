# osu! Skin Selection Tool - Project Context

## Project Overview
A JavaFX-based desktop application for managing and previewing osu! skins.

## Current Status
- ✅ **Phase 1 Complete**: Initial setup, compilation, and testing
- ✅ **Phase 2 Complete**: Default osu! path detection and directory selection
- 🔄 **Next Phase**: TBD - awaiting user direction

## Architecture
- **Language**: Java 17
- **Framework**: JavaFX 19
- **Build Tool**: Maven
- **Main Class**: `com.osuskin.tool.OsuSkinToolApplication`

## Key Features Implemented
1. **Directory Selection with Smart Defaults**
   - Auto-detects osu! installation paths (Windows/macOS/Linux)
   - Validates selected directories contain osu! skins
   - Shows helpful suggestions in UI

2. **Skin Scanning System**
   - Scans skin directories for osu! skin files
   - Parses skin.ini files for metadata
   - Counts elements (images, audio, etc.)

3. **UI Components**
   - Main window with skin list
   - Search and filter functionality  
   - Sorting options
   - Details panel for selected skins

## Project Structure
```
src/main/java/com/osuskin/tool/
├── OsuSkinToolApplication.java    # Main application class
├── controller/
│   └── MainController.java        # Main UI controller
├── model/
│   ├── Configuration.java         # App configuration
│   ├── Skin.java                  # Skin model
│   └── SkinElement.java          # Skin element model
├── service/
│   ├── ConfigurationService.java # Config management
│   └── SkinScannerService.java   # Skin scanning logic
├── util/
│   ├── ConfigurationManager.java # Config file I/O
│   └── OsuPathDetector.java      # osu! path detection (NEW)
└── view/                         # Future UI components
```

## Commands to Remember
- **Compile**: `mvn clean compile`
- **Test**: `mvn test`  
- **Run**: `mvn javafx:run`
- **Build JAR**: `mvn clean package`

## Default osu! Paths
- **Windows**: `%LOCALAPPDATA%\osu!\Skins\`
- **macOS**: `~/Library/Application Support/osu!/Skins/`
- **Linux**: `~/.local/share/osu!/Skins/`

## Recent Changes
- Added `OsuPathDetector` class for smart path detection
- Updated `MainController` to suggest default osu! paths
- Added directory validation with user confirmation
- Successfully tested with 30 skins in user's osu! directory

## TODOs for Next Session
- [ ] Implement skin preview functionality
- [ ] Add grid view toggle
- [ ] Create settings dialog
- [ ] Implement favorites system
- [ ] Add skin export/import features
- [ ] Create thumbnail generation
- [ ] Add audio preview capability

## Notes
- Application successfully detects and works with existing osu! installations
- No issues with JavaFX setup or dependencies
- All basic functionality tested and working