# Performance Optimization Plan

## Current Performance Issues

### 1. Skin Loading Issues
- **UI Stutter**: Application freezes when skin is clicked due to synchronous loading
- **Progress Bar**: Not smooth or fine-grained enough during element loading
- **Preview Timing**: Visual preview lingers between skin switches instead of clean transitions
- **Excessive File Searches**: Many "element not found" messages indicate unnecessary file system operations

### 2. Resource Loading Inefficiencies
- Loading all possible elements instead of only existing ones
- Attempting to load up to 200 scorebar-color frames when only specific percentages are used
- No pre-initialization of renderers at application startup

## Optimization Strategies

### Immediate Actions

#### 1. Asynchronous Loading Architecture
- Move all skin loading to background threads
- Implement proper Task/Service pattern for non-blocking UI
- Use Platform.runLater() only for UI updates
- Add loading state management to prevent multiple concurrent loads

#### 2. Priority-Based Progressive Loading
- Load elements in priority waves for instant preview appearance
- **Critical Priority** (show preview immediately): hitcircle, cursor, approachcircle
- **High Priority**: numbers, hit bursts, sliders
- **Medium Priority**: UI elements, scorebar
- **Low Priority**: menu elements, sounds, miscellaneous
- Update preview after each priority level completes

#### 3. Skin Directory Indexing & Caching
- Create `.skintool-index.json` in each skin directory
- Index contains: available elements, frame counts, skin metadata
- **Staleness Detection**:
  - Check directory modification time
  - Validate skin.ini hash
  - Verify file count matches
  - NO time-based expiration (changes are rare)
- Rebuild index only when:
  - Index doesn't exist
  - Directory modified
  - Manual refresh
  - File count mismatch

#### 4. Animation Frame Optimization
- For scorebar-colour: Always load static `scorebar-colour` as fallback, then check for animation
- Smart frame sampling - load maximum 10 animation frames regardless of total count
- Use exponential/binary search to find frame range (e.g., 0 to N)
- Sample frames evenly across the range (if >10 frames, load at intervals)
- 95% reduction for skins with 200+ frames (load 10 instead of 200)
- Preserves fallback behavior when no animation frames exist

#### 5. Renderer Pre-initialization
- Initialize GameplayRenderer at application startup
- Pre-create Canvas and GraphicsContext
- Cache default skin elements at startup
- Keep renderers in standby mode between previews

### Performance Improvements to Implement

#### Phase 1: Core Loading Optimization
1. **Priority-Based Loading System**
   - Implement LoadPriority enum (CRITICAL, HIGH, MEDIUM, LOW)
   - Load and display critical elements in <50ms
   - Progressive enhancement as more elements load
   - Show functional preview before all elements are ready

2. **Skin Index Cache Implementation**
   - Generate `.skintool-index.json` per skin
   - Store element list, frame counts, metadata
   - Quick validation via directory mod time and file count
   - Instant loading after first scan

3. **Smart Element Discovery**
   - Use cached index to know what exists
   - Zero filesystem checks for missing elements
   - Batch existence checks when index rebuild needed

#### Phase 2: Resource Management
1. **Selective Frame Loading**
   - For scorebar-color: Load only frames at [0, 25, 50, 75, 100] percentages
   - For hit bursts: Load only used frame counts
   - Implement frame skipping for animations

2. **Memory Management**
   - Implement LRU cache for skin elements
   - Release unused resources immediately
   - Use weak references for non-critical elements

3. **Preview Lifecycle**
   - Clear preview immediately on skin change
   - Pre-buffer next skin if predictable
   - Implement clean transition states

#### Phase 3: Advanced Optimizations
1. **Skin Indexing**
   - Create index file per skin with available elements
   - Cache skin metadata
   - Quick lookup without file system access

2. **Lazy Loading**
   - Load only visible elements initially
   - Background load remaining elements
   - Priority queue for element loading

3. **Image Optimization**
   - Downsample large images for preview
   - Use appropriate image formats
   - Implement texture atlasing for small elements

## Implementation Priority

### High Priority
1. Fix UI stutter with async loading
2. Implement element existence checking
3. Smooth progress bar updates
4. Clean preview transitions
5. Selective scorebar frame loading

### Medium Priority
1. Pre-initialize renderers at startup
2. Implement skin manifest/indexing
3. Memory management improvements
4. Parallel element loading

### Low Priority
1. Advanced caching strategies
2. Image optimization
3. Predictive loading

## Performance Metrics to Track
- Skin load time (ms)
- UI thread block time
- Memory usage per skin
- File system operations count
- Frame drops during preview

## Code Areas to Modify
- `SkinElementLoader`: Implement async loading and existence checking
- `MainController`: Fix preview lifecycle and progress updates
- `GameplayRenderer`: Pre-initialization and resource management
- `SkinScannerService`: Build element manifests
- `Skin` model: Add element existence tracking

### Additional Optimizations

#### 6. Lazy Audio Loading
- Don't load audio files until needed for playback
- Audio files are large and block initial preview
- Preload only critical sounds (hitnormal) in background
- Load other sounds on-demand when user clicks play

#### 7. Canvas Dirty Rectangle Optimization  
- Track which regions of canvas changed
- Only clear and redraw changed portions
- Reduces rendering work by 60-80%
- Especially effective for cursor movement and hit bursts

#### 8. Shared Resource Pool
- Cache commonly used default elements across skins
- Use WeakReferences for automatic memory management
- Avoid reloading same default elements repeatedly
- Share resources between skins that use defaults

#### 9. JVM and JavaFX Tuning
- Use G1GC for better pause times: `-XX:+UseG1GC`
- Preallocate heap: `-Xms256m -Xmx512m`
- Disable antialiasing: `gc.setImageSmoothing(false)`
- Enable hardware acceleration for canvas
- Cache complex nodes with `setCache(true)`

#### 10. Smart Garbage Collection
- Schedule GC during idle times, not during animations
- Detect when not animating to trigger cleanup
- Prevent stuttering from GC pauses
- Monitor memory pressure and clean proactively

## Expected Improvements
- **With Priority Loading**: Preview appears in <50ms (vs 500-1000ms)
- **With Indexing**: Second load of any skin is instant (<20ms)
- **Combined optimizations**: 80-90% reduction in load time
- **File operations**: 95% reduction (index eliminates searches)
- **Memory usage**: 50% reduction with smart loading
- **UI responsiveness**: Zero frame drops, consistent 60 FPS