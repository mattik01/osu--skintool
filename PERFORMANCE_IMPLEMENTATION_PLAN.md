# Performance Optimization Implementation Plan

## Key Issues Identified

### 1. Synchronous UI Blocking
- **Problem**: `displaySkinPreview()` in MainController runs on UI thread, causing freezes
- **Location**: MainController.java:764-809
- **Impact**: UI completely frozen during skin loading

### 2. Excessive File System Operations
- **Problem**: SkinElementLoader tries to load every possible element, even non-existent ones
- **Location**: SkinElementLoader.java - multiple tryLoad methods
- **Impact**: Hundreds of failed file checks per skin load

### 3. Scorebar Animation Overload
- **Problem**: Loading up to 200 scorebar-colour frames when only ~5 are needed
- **Location**: GameplayUI.java:89-96
- **Impact**: Unnecessary memory usage and I/O operations

### 4. No Element Pre-scanning
- **Problem**: No manifest of existing elements, causing blind searches
- **Location**: Throughout SkinElementLoader
- **Impact**: Wasted I/O on non-existent files

### 5. Progress Bar Granularity
- **Problem**: Progress updates only on major steps, not per-element
- **Location**: LazyLoadingSkinService.java:236-361
- **Impact**: Progress bar jumps instead of smooth progression

### 6. Renderer Lifecycle Issues
- **Problem**: Renderers created per skin selection, not pre-initialized
- **Location**: MainController.java:784-788
- **Impact**: Initialization overhead on every skin switch

## Implementation Steps

### Phase 1: Priority-Based Loading System

#### 1.1 Implement Priority Loading
```java
public enum LoadPriority {
    CRITICAL(0),   // hitcircle, cursor, approachcircle - needed immediately
    HIGH(1),       // numbers, hit bursts, sliders - needed soon  
    MEDIUM(2),     // scorebar, UI elements - nice to have
    LOW(3);        // menu elements, sounds - can wait
    
    private final int order;
    
    // Element classification
    private static final Map<String, LoadPriority> ELEMENT_PRIORITIES = Map.of(
        "hitcircle", CRITICAL,
        "cursor", CRITICAL,
        "approachcircle", CRITICAL,
        "default-", HIGH,
        "hit", HIGH,
        "slider", HIGH,
        "scorebar", MEDIUM,
        "menu", LOW
    );
    
    public static LoadPriority getPriority(String elementName) {
        return ELEMENT_PRIORITIES.entrySet().stream()
            .filter(e -> elementName.startsWith(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(MEDIUM);
    }
}

public class PriorityLoader {
    public void loadSkinWithPriority(Skin skin, Runnable onCriticalLoaded) {
        Path skinDir = skin.getDirectoryPathAsPath();
        
        // Load critical elements first
        CompletableFuture<Void> critical = loadPriorityGroup(skinDir, LoadPriority.CRITICAL);
        
        critical.thenRun(() -> {
            // Show preview immediately with critical elements
            Platform.runLater(onCriticalLoaded);
            
            // Continue loading in background
            loadPriorityGroup(skinDir, LoadPriority.HIGH)
                .thenRun(() -> Platform.runLater(this::updatePreview));
            
            loadPriorityGroup(skinDir, LoadPriority.MEDIUM)
                .thenRun(() -> Platform.runLater(this::updatePreview));
                
            loadPriorityGroup(skinDir, LoadPriority.LOW);
        });
    }
}
```

### Phase 2: Skin Directory Indexing

#### 2.1 Create Skin Index System
```java
public class SkinIndexCache {
    private static final String INDEX_FILE = ".skintool-index.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    
    public static class SkinIndex {
        public String version = "1.0";
        public long directoryModTime;
        public String skinIniHash;
        public int fileCount;
        public Set<String> availableElements = new HashSet<>();
        public Map<String, Integer> animationFrameCounts = new HashMap<>();
        public Map<String, String> metadata = new HashMap<>(); // name, author, version
        
        public boolean isValid(Path skinDir) {
            try {
                // Check directory modification time
                long currentModTime = Files.getLastModifiedTime(skinDir).toMillis();
                if (currentModTime != directoryModTime) {
                    return false;
                }
                
                // Check file count for quick validation
                long currentCount = Files.list(skinDir).count();
                if (currentCount != fileCount) {
                    return false;
                }
                
                // Check skin.ini hash if it exists
                Path skinIni = skinDir.resolve("skin.ini");
                if (Files.exists(skinIni)) {
                    String currentHash = calculateHash(skinIni);
                    if (!currentHash.equals(skinIniHash)) {
                        return false;
                    }
                }
                
                return true;
            } catch (IOException e) {
                return false; // Rebuild on any error
            }
        }
    }
    
    public SkinIndex loadOrCreateIndex(Path skinDir) {
        Path indexPath = skinDir.resolve(INDEX_FILE);
        
        // Try to load existing index
        if (Files.exists(indexPath)) {
            try {
                SkinIndex index = mapper.readValue(indexPath.toFile(), SkinIndex.class);
                if (index.isValid(skinDir)) {
                    return index; // Use cached index
                }
            } catch (Exception e) {
                // Fall through to rebuild
            }
        }
        
        // Build new index
        return rebuildIndex(skinDir);
    }
    
    private SkinIndex rebuildIndex(Path skinDir) throws IOException {
        SkinIndex index = new SkinIndex();
        
        // Set directory info
        index.directoryModTime = Files.getLastModifiedTime(skinDir).toMillis();
        index.fileCount = (int) Files.list(skinDir).count();
        
        // Hash skin.ini if exists
        Path skinIni = skinDir.resolve("skin.ini");
        if (Files.exists(skinIni)) {
            index.skinIniHash = calculateHash(skinIni);
            parseSkinIniMetadata(skinIni, index.metadata);
        }
        
        // Scan for available elements
        Files.list(skinDir)
            .filter(Files::isRegularFile)
            .forEach(file -> {
                String name = file.getFileName().toString();
                String baseName = removeExtension(name);
                
                index.availableElements.add(baseName);
                
                // Check for animation sequences
                if (baseName.matches(".*-\\d+$")) {
                    String animBase = baseName.replaceAll("-\\d+$", "");
                    index.animationFrameCounts.merge(animBase, 1, Integer::sum);
                }
            });
        
        // Save index to disk
        Path indexPath = skinDir.resolve(INDEX_FILE);
        mapper.writerWithDefaultPrettyPrinter()
            .writeValue(indexPath.toFile(), index);
        
        // Mark as hidden on Windows
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            Files.setAttribute(indexPath, "dos:hidden", true);
        }
        
        return index;
    }
}
```

#### 2.2 Integrate Index with Element Loader
```java
public class SkinElementLoader {
    private SkinIndexCache.SkinIndex currentIndex;
    
    public void setSkinDirectory(Path skinDirectory) {
        this.skinDirectory = skinDirectory;
        // Load or create index
        this.currentIndex = SkinIndexCache.loadOrCreateIndex(skinDirectory);
        clearCache();
    }
    
    public boolean elementExists(String elementName) {
        // Use index for O(1) lookup instead of filesystem check
        if (currentIndex != null) {
            return currentIndex.availableElements.contains(elementName) ||
                   currentIndex.availableElements.contains(elementName + "@2x");
        }
        // Fallback to filesystem check
        return checkFileSystem(elementName);
    }
    
    public int getAnimationFrameCount(String baseName) {
        if (currentIndex != null) {
            return currentIndex.animationFrameCounts.getOrDefault(baseName, 0);
        }
        return 0;
    }
}
```

### Phase 3: Async Loading & UI Responsiveness

#### 3.1 Create AsyncSkinLoader class
```java
public class AsyncSkinLoader {
    private final ExecutorService executor = ForkJoinPool.commonPool();
    private CompletableFuture<SkinLoadResult> currentLoad;
    
    public CompletableFuture<SkinLoadResult> loadSkinAsync(Path skinPath) {
        // Cancel previous load if exists
        if (currentLoad != null && !currentLoad.isDone()) {
            currentLoad.cancel(true);
        }
        
        currentLoad = CompletableFuture.supplyAsync(() -> {
            SkinLoadResult result = new SkinLoadResult();
            result.manifest = scanAvailableElements(skinPath);
            result.loader = new SkinElementLoader(skinPath);
            result.loader.setManifest(result.manifest);
            return result;
        }, executor);
        
        return currentLoad;
    }
}
```

#### 1.2 Modify MainController displaySkinPreview
- Move all loading to background thread
- Update UI only with Platform.runLater()
- Clear preview immediately on skin change
- Show loading indicator with smooth progress

### Phase 2: Element Manifest System

#### 2.1 Create SkinElementManifest class
```java
public class SkinElementManifest {
    private final Set<String> availableElements;
    private final Map<String, Path> elementPaths;
    private final Map<String, Integer> animationFrameCounts;
    
    public static SkinElementManifest scan(Path skinDir) {
        // Single directory scan to build manifest
        // Cache file extensions and paths
        // Count animation frames
    }
}
```

#### 2.2 Modify SkinElementLoader
- Add manifest support
- Only attempt to load elements that exist
- Skip file system checks for non-existent elements

### Phase 3: Optimized Scorebar Loading

#### 3.1 Smart Frame Sampling
```java
// In GameplayUI.java
private static final int MAX_SCOREBAR_FRAMES = 10;

private void loadScorebarFrames(SkinElementLoader loader) {
    // IMPORTANT: Always load static scorebar-colour first as fallback
    scorebarColour = loader.loadImage("scorebar-colour");
    
    // Step 1: Find actual frame range using efficient search
    int minFrame = 0;
    int maxFrame = findMaxScorebarFrame(loader);
    
    if (maxFrame < 0) {
        // No numbered frames found - will use static scorebarColour as fallback
        return;
    }
    
    // Step 2: Calculate which frames to sample
    List<Image> frames = new ArrayList<>();
    int totalFrames = maxFrame - minFrame + 1;
    
    if (totalFrames <= MAX_SCOREBAR_FRAMES) {
        // Load all frames if 10 or fewer
        for (int i = minFrame; i <= maxFrame; i++) {
            Image frame = loader.loadImage("scorebar-colour-" + i);
            if (frame != null) frames.add(frame);
        }
    } else {
        // Sample evenly across the range
        double interval = (double)(maxFrame - minFrame) / (MAX_SCOREBAR_FRAMES - 1);
        for (int i = 0; i < MAX_SCOREBAR_FRAMES; i++) {
            int frameNum = minFrame + (int)(i * interval);
            Image frame = loader.loadImage("scorebar-colour-" + frameNum);
            if (frame != null) frames.add(frame);
        }
    }
    
    if (!frames.isEmpty()) {
        scorebarColourFrames = frames.toArray(new Image[0]);
    }
}

private int findMaxScorebarFrame(SkinElementLoader loader) {
    // Exponential search to find upper bound
    int frame = 0;
    int step = 10;
    
    while (frame < 500 && loader.elementExists("scorebar-colour-" + frame)) {
        frame += step;
        step = Math.min(step * 2, 50); // Cap step size
    }
    
    // Binary search for exact last frame
    int left = Math.max(0, frame - step);
    int right = frame;
    
    while (left < right) {
        int mid = (left + right + 1) / 2;
        if (loader.elementExists("scorebar-colour-" + mid)) {
            left = mid;
        } else {
            right = mid - 1;
        }
    }
    
    return left;
}
```

### Phase 4: Progress Reporting Enhancement

#### 4.1 Fine-grained Progress Updates
```java
public class ProgressiveLoader {
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty status = new SimpleStringProperty("");
    
    public void loadElements(List<String> elements) {
        int total = elements.size();
        AtomicInteger loaded = new AtomicInteger(0);
        
        elements.parallelStream().forEach(element -> {
            loadElement(element);
            int current = loaded.incrementAndGet();
            Platform.runLater(() -> {
                progress.set((double)current / total);
                status.set(String.format("Loading %s (%d/%d)", 
                    element, current, total));
            });
        });
    }
}
```

### Phase 5: Renderer Pre-initialization

#### 5.1 Application Startup Initialization
```java
// In MainController.initialize()
private void initializeRenderers() {
    // Create renderers once at startup
    elementLoader = new SkinElementLoader(null);
    enhancedRenderer = new GameplayRenderer(gameplayCanvas, elementLoader);
    simpleRenderer = new SimpleGameplayRenderer(gameplayCanvas, elementLoader);
    
    // Pre-initialize with default skin
    enhancedRenderer.initialize();
    simpleRenderer.initialize();
}
```

#### 5.2 Renderer Reuse
```java
private void switchSkin(Path skinPath) {
    // Stop current animation
    if (animationTimer != null) {
        animationTimer.stop();
    }
    
    // Clear canvas immediately
    GraphicsContext gc = gameplayCanvas.getGraphicsContext2D();
    gc.clearRect(0, 0, gameplayCanvas.getWidth(), gameplayCanvas.getHeight());
    
    // Update loader path (reuse existing loader)
    elementLoader.setSkinDirectory(skinPath);
    elementLoader.clearCache();
    
    // Restart animation with new skin
    startAutoplayAnimation();
}
```

### Phase 6: Parallel Loading Strategy

#### 6.1 Multi-threaded Element Loading
```java
public class ParallelSkinLoader {
    private final ForkJoinPool pool = new ForkJoinPool(4);
    
    public CompletableFuture<Void> loadSkinElements(Skin skin, SkinElementManifest manifest) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Load different element types in parallel
        futures.add(loadVisualElements(manifest));
        futures.add(loadAudioElements(manifest));
        futures.add(loadUIElements(manifest));
        futures.add(loadAnimations(manifest));
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
```

## Performance Targets

### Metrics to Achieve
- **Skin Load Time**: < 200ms for average skin (from 500-1000ms)
- **UI Response**: Zero frame drops during loading
- **File Operations**: 80% reduction (only check existing files)
- **Memory Usage**: 50% reduction (selective frame loading)
- **Progress Updates**: 60 FPS smooth progression

### Benchmarking Points
1. Time from skin click to preview display
2. Number of file system operations per load
3. UI thread block time
4. Memory allocated per skin
5. Progress bar update frequency

## Testing Strategy

### Performance Tests
1. Load time benchmark with various skin sizes
2. UI responsiveness test during loading
3. Memory profiling for different skins
4. File operation counting
5. Progress bar smoothness measurement

### Regression Tests
1. Ensure all existing features work
2. Verify skin element display correctness
3. Test animation playback
4. Validate audio preview functionality
5. Check skin switching reliability

## Additional Optimizations

### Lazy Audio Loading
```java
public class LazyAudioLoader {
    private final Map<String, CompletableFuture<Media>> audioCache = new ConcurrentHashMap<>();
    
    public CompletableFuture<Media> getAudioAsync(String element) {
        return audioCache.computeIfAbsent(element, key ->
            CompletableFuture.supplyAsync(() -> {
                // Load audio in background thread
                return loadAudioFile(key);
            })
        );
    }
    
    public void preloadCritical() {
        // Only preload the most common sound
        getAudioAsync("normal-hitnormal");
    }
}
```

### Canvas Dirty Rectangle Tracking
```java
public class OptimizedRenderer {
    private final Set<Rectangle2D> dirtyRegions = new HashSet<>();
    private boolean fullRedrawNeeded = true;
    
    public void markDirty(double x, double y, double width, double height) {
        dirtyRegions.add(new Rectangle2D(x, y, width, height));
    }
    
    public void render() {
        if (fullRedrawNeeded) {
            // Full redraw on first frame or major changes
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            renderEverything();
            fullRedrawNeeded = false;
        } else if (!dirtyRegions.isEmpty()) {
            // Partial redraw for animations
            for (Rectangle2D region : dirtyRegions) {
                gc.save();
                gc.beginPath();
                gc.rect(region.getX(), region.getY(), 
                       region.getWidth(), region.getHeight());
                gc.clip();
                gc.clearRect(region.getX(), region.getY(),
                           region.getWidth(), region.getHeight());
                renderRegion(region);
                gc.restore();
            }
            dirtyRegions.clear();
        }
    }
}
```

### Shared Resource Pool
```java
public class SharedResourcePool {
    private static final Map<String, WeakReference<Image>> sharedImages = new ConcurrentHashMap<>();
    
    public static Image getOrLoad(String key, Supplier<Image> loader) {
        WeakReference<Image> ref = sharedImages.get(key);
        Image image = (ref != null) ? ref.get() : null;
        
        if (image == null) {
            image = loader.get();
            if (image != null) {
                sharedImages.put(key, new WeakReference<>(image));
            }
        }
        return image;
    }
    
    public static void cleanStaleReferences() {
        sharedImages.entrySet().removeIf(e -> e.getValue().get() == null);
    }
}
```

## Rollout Plan

### Phase 1: Critical Performance (Week 1)
- Implement Priority Loading system
- Add Skin Index caching
- Update MainController for progressive loading

### Phase 2: Core Optimizations (Week 2)
- Implement AsyncSkinLoader
- Add selective scorebar frame loading
- Integrate lazy audio loading

### Phase 3: Rendering Optimizations (Week 3)
- Add dirty rectangle tracking
- Implement shared resource pool
- Pre-initialize renderers at startup

### Phase 4: Fine Tuning (Week 4)
- JVM and JavaFX tuning
- Smart GC management
- Performance benchmarking
- Bug fixes and refinement

## Code Changes Summary

### Files to Modify
1. `MainController.java` - Async loading, renderer pre-init
2. `SkinElementLoader.java` - Manifest support, existence checking
3. `GameplayUI.java` - Selective scorebar frame loading
4. `LazyLoadingSkinService.java` - Fine-grained progress

### New Files to Create
1. `AsyncSkinLoader.java` - Async loading orchestration
2. `SkinElementManifest.java` - Element existence tracking
3. `ProgressiveLoader.java` - Smooth progress reporting
4. `ParallelSkinLoader.java` - Multi-threaded loading

### Configuration Changes
- Add performance tuning settings
- Configurable thread pool sizes
- Optional aggressive caching mode