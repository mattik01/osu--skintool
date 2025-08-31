# osu! Skin Tool Architecture

## Core Architecture

### Service Layer

#### Active Services
- **LazyLoadingSkinService**: Primary service for loading skins with lazy initialization
- **SkinContainerService**: Manages skin containers and extraction
- **AsyncPreviewLoader**: Handles asynchronous preview loading with thread pool
- **AudioMixerService**: Manages audio preview and hitsound playback
- **ManifestCache**: Caches skin element manifests for performance
- **DefaultSkinCache**: Singleton cache for default skin elements
- **SkinElementLoader**: Loads individual skin elements with fallback support

#### Performance Services
- **SkinManifestBuilder**: Builds element manifests during scanning
- **SkinElementManifest**: Tracks available elements in a skin
- **PerformanceMonitor**: Monitors and logs performance metrics

### Model Layer
- **Skin**: Core domain model with metadata and element management
- **SkinContainer**: Manages extracted skin containers
- **SkinElement**: Represents individual skin files
- **Configuration**: Application settings and preferences
- **PreviewElements**: Defines elements needed for preview
- **ElementGroup**: Groups elements by priority for loading

### View Layer
- **GameplayRenderer**: Full gameplay preview with animations
- **SimpleGameplayRenderer**: Basic preview renderer (legacy)
- **GameplayUI**: Overlay UI elements (health, score, combo)
- **HitObject**: Base class for gameplay objects
- **HitCircle/Slider**: Specific hit object implementations
- **SharedElementCache**: Shared cache for gameplay elements

## Performance Optimizations

### Manifest-Based Loading
- Pre-scan directories to build element manifests
- Avoid checking for non-existent files
- Load only elements that exist and are needed

### Async Operations
- All skin loading is asynchronous via AsyncPreviewLoader
- Thread pool with configurable size (default: 4 threads)
- Fine-grained progress reporting

### Animation Optimization
- Smart frame sampling for scorebar-colour (max 10 frames)
- Binary search for finding animation frame ranges
- Weak references for non-critical frames

### Caching Strategy
- Manifest cache persists between sessions
- Default skin cache initialized at startup
- Shared element cache for gameplay renderers

## Data Flow

1. **Initialization**
   - ConfigurationManager loads settings
   - DefaultSkinCache initializes with default elements
   - ManifestCache loads persisted manifests

2. **Skin Loading**
   - LazyLoadingSkinService creates loading task
   - SkinManifestBuilder scans directory once
   - AsyncPreviewLoader loads elements in parallel
   - Progress updates sent to UI

3. **Preview Rendering**
   - GameplayRenderer receives loaded elements
   - SharedElementCache provides common elements
   - Animation timer drives gameplay simulation
   - AudioMixerService handles sound playback

## Key Design Patterns

- **Singleton**: DefaultSkinCache, ManifestCache
- **Task/Future**: Async operations with CompletableFuture
- **Observer**: Progress updates via Task properties
- **Strategy**: Different loading priorities via ElementGroup
- **Cache-aside**: Manifest and element caching