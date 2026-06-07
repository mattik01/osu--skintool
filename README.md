# osu! Skin Tool

> ## 🚧 WORK IN PROGRESS — NOT COMPLETED 🚧
> Actively developed and **not finished**. Features and internals may change or
> break. This is a project I intend to finish and keep **open source** (no
> profit/monetization). Use at your own risk for now.

A high-performance JavaFX application for managing and previewing osu! skins with real-time gameplay simulation.

## Features

- **Fast Skin Loading**: Asynchronous loading with manifest-based optimization
- **Real-time Preview**: Full gameplay simulation with animations and hitsounds
- **Smart Extraction**: Handles compressed skins with nested folder detection
- **Audio Playback**: Preview audio samples, hitsounds, and UI sounds
- **Performance Optimized**: Lazy loading, caching, and smart frame sampling

## Requirements

- Java 17 or later
- JavaFX 19 (included)
- Maven 3.6+

## Quick Start

```bash
# Build
mvn clean package

# Run
mvn javafx:run
```

## Project Structure

```
src/main/java/com/osuskin/tool/
├── controller/     # UI controllers
├── model/         # Domain models
├── service/       # Business logic
├── view/          # Custom UI components
└── util/          # Utilities

docs/
├── ARCHITECTURE.md     # System architecture
└── OSU_SKIN_RENDERING.md  # osu! skin specifications
```

## Key Technologies

- **JavaFX 19**: UI framework
- **Jackson**: JSON serialization
- **SLF4J/Logback**: Logging
- **Maven Shade**: Fat JAR packaging

## Performance Features

- Manifest-based element loading
- Parallel asset loading with thread pools
- Smart animation frame sampling (10 frame max)
- Shared element caching
- Lazy skin initialization

## License

MIT