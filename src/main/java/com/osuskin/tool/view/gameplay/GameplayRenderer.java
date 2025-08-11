package com.osuskin.tool.view.gameplay;

import com.osuskin.tool.service.SkinElementLoader;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Enhanced gameplay renderer with proper hit objects, animations, and effects.
 */
public class GameplayRenderer {
    
    private static final Logger logger = LoggerFactory.getLogger(GameplayRenderer.class);
    
    private final Canvas canvas;
    private final SkinElementLoader elementLoader;
    private final GraphicsContext gc;
    
    // Skin elements
    private Image hitCircle;
    private Image hitCircleOverlay;
    private Image approachCircle;
    private Image cursor;
    private Image cursorTrail;
    private Image lightingImage;
    private Image[] defaultNumbers = new Image[10];
    
    // Slider elements
    private Image sliderBody;
    private Image sliderBall;
    private Image sliderFollowCircle;
    private Image reverseArrow;
    
    // Hit burst images
    private Map<HitObject.HitResult, Image[]> hitBurstFrames = new HashMap<>();
    
    // Game objects
    private List<HitObject> hitObjects = new ArrayList<>();
    private List<HitBurst> activeHitBursts = new ArrayList<>();
    
    // Combo colors
    private List<javafx.scene.paint.Color> comboColors = new ArrayList<>();
    private int currentComboColorIndex = 0;
    
    // UI system
    private GameplayUI gameplayUI;
    
    // Cursor trail
    private LinkedList<CursorTrailPoint> cursorTrailPoints = new LinkedList<>();
    private static final int MAX_TRAIL_POINTS = 20;
    
    // Animation state
    private double currentTime = 0;
    private double loopDuration = 8.0;  // Total loop time
    
    // Rendering constants
    private static final double BASE_CIRCLE_SIZE = 80;  // Increased for larger preview
    private static final double REFERENCE_WIDTH = 800;
    private static final double REFERENCE_HEIGHT = 600;
    
    // Dynamic scaling (disabled - using fixed size)
    private double canvasScale = 1.0;
    private double circleSize = BASE_CIRCLE_SIZE;
    private double hitCircleScale = 1.0;
    private double overlayScale = 1.0;
    private double approachCircleScale = 1.0;
    
    // Statistics
    private int combo = 0;
    private int score = 0;
    private double accuracy = 100.0;
    private int totalHits = 0;
    private int perfect300 = 0;
    private int good100 = 0;
    private int meh50 = 0;
    
    public GameplayRenderer(Canvas canvas, SkinElementLoader elementLoader) {
        this.canvas = canvas;
        this.elementLoader = elementLoader;
        this.gc = canvas.getGraphicsContext2D();
        this.gameplayUI = new GameplayUI(gc);
    }
    
    public void initialize() {
        loadElements();
        gameplayUI.loadElements(elementLoader);
        calculateCanvasScale();
        initializeComboColors();
        setupHitObjects();
        logger.info("GameplayRenderer initialized with enhanced features");
    }
    
    /**
     * Calculate scale factor based on canvas size.
     * Currently disabled - using fixed size for preview.
     */
    private void calculateCanvasScale() {
        // Fixed scale - no dynamic scaling
        canvasScale = 1.0;
        circleSize = BASE_CIRCLE_SIZE;
        
        // Recalculate element scales with the fixed circle size
        calculateElementScales();
        
        // Update UI scale (fixed at 1.0)
        gameplayUI.setScale(1.0);
        
        logger.debug("Using fixed scale: {} (canvas: {}x{})", 
                    canvasScale, canvas.getWidth(), canvas.getHeight());
    }
    
    /**
     * Handle canvas resize.
     */
    public void onCanvasResize() {
        calculateCanvasScale();
        setupHitObjects(); // Recalculate positions
    }
    
    private void loadElements() {
        // Load basic elements
        hitCircle = elementLoader.loadImage("hitcircle");
        hitCircleOverlay = elementLoader.loadImage("hitcircleoverlay");
        approachCircle = elementLoader.loadImage("approachcircle");
        cursor = elementLoader.loadImage("cursor");
        cursorTrail = elementLoader.loadImage("cursortrail");
        lightingImage = elementLoader.loadImage("lighting");
        
        // Load slider elements
        sliderBody = elementLoader.loadImage("sliderb");
        sliderBall = elementLoader.loadImage("sliderball");
        sliderFollowCircle = elementLoader.loadImage("sliderfollowcircle");
        reverseArrow = elementLoader.loadImage("reversearrow");
        
        // Calculate scales
        calculateElementScales();
        
        // Load numbers
        for (int i = 0; i < 10; i++) {
            defaultNumbers[i] = elementLoader.loadImage("default-" + i);
        }
        
        // Load hit burst animations
        loadHitBurstImages();
        
        logger.debug("Loaded all elements including hit bursts, lighting, and sliders");
    }
    
    private void loadHitBurstImages() {
        // Try to load animated frames first, fall back to static
        for (HitObject.HitResult result : HitObject.HitResult.values()) {
            if (result == HitObject.HitResult.NONE) continue;
            
            String prefix = getHitBurstPrefix(result);
            List<Image> frames = new ArrayList<>();
            
            // Try loading animated frames (hit300-0.png, hit300-1.png, etc.)
            for (int i = 0; i < 10; i++) {
                Image frame = elementLoader.loadImage(prefix + "-" + i);
                if (frame != null) {
                    frames.add(frame);
                } else {
                    break;  // No more frames
                }
            }
            
            // If no animated frames, try static image
            if (frames.isEmpty()) {
                Image staticImage = elementLoader.loadImage(prefix);
                if (staticImage != null) {
                    frames.add(staticImage);
                }
            }
            
            if (!frames.isEmpty()) {
                hitBurstFrames.put(result, frames.toArray(new Image[0]));
                logger.debug("Loaded {} frames for {}", frames.size(), result);
            }
        }
    }
    
    private String getHitBurstPrefix(HitObject.HitResult result) {
        switch (result) {
            case HIT_300: return "hit300";
            case HIT_100: return "hit100";
            case HIT_50: return "hit50";
            case MISS: return "hit0";
            default: return "";
        }
    }
    
    private void calculateElementScales() {
        // Establish consistent scaling for all elements based on hit circle
        if (hitCircle != null) {
            double hitCircleSize = Math.max(hitCircle.getWidth(), hitCircle.getHeight());
            hitCircleScale = circleSize / hitCircleSize;
            
            // Overlay should match hit circle size exactly
            if (hitCircleOverlay != null) {
                double overlaySize = Math.max(hitCircleOverlay.getWidth(), hitCircleOverlay.getHeight());
                overlayScale = circleSize / overlaySize;
            } else {
                overlayScale = hitCircleScale;
            }
            
            // Approach circle uses same base scale
            if (approachCircle != null) {
                double approachSize = Math.max(approachCircle.getWidth(), approachCircle.getHeight());
                approachCircleScale = circleSize / approachSize;
            } else {
                approachCircleScale = hitCircleScale;
            }
            
            logger.debug("Consistent scaling established - base scale: {}", hitCircleScale);
        } else {
            // Fallback scaling
            hitCircleScale = 1.0;
            overlayScale = 1.0;
            approachCircleScale = 1.0;
        }
    }
    
    private void initializeComboColors() {
        comboColors.clear();
        
        // Try to get combo colors from the current skin
        com.osuskin.tool.model.Skin currentSkin = elementLoader.getCurrentSkin();
        if (currentSkin != null && currentSkin.getComboColors() != null && !currentSkin.getComboColors().isEmpty()) {
            // Use skin's combo colors
            for (int[] rgb : currentSkin.getComboColors()) {
                comboColors.add(javafx.scene.paint.Color.rgb(rgb[0], rgb[1], rgb[2]));
            }
            logger.info("Loaded {} combo colors from skin", comboColors.size());
        } else {
            // Use default osu! combo colors if skin doesn't specify any
            comboColors.add(javafx.scene.paint.Color.rgb(255, 192, 0));   // Orange
            comboColors.add(javafx.scene.paint.Color.rgb(0, 202, 0));     // Green  
            comboColors.add(javafx.scene.paint.Color.rgb(18, 124, 255));  // Blue
            comboColors.add(javafx.scene.paint.Color.rgb(242, 24, 57));   // Red
            logger.info("Using default combo colors");
        }
        
        currentComboColorIndex = 0;
    }
    
    private void setupHitObjects() {
        hitObjects.clear();
        currentComboColorIndex = 0;  // Reset color index
        
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        
        // Create timeline with proper spacing accounting for slider durations
        // Increased spacing between elements for better readability
        
        hitObjects.add(new HitCircle(width * 0.3, height * 0.4, 1.0, 1));
        hitObjects.add(new HitCircle(width * 0.5, height * 0.3, 1.5, 2));
        hitObjects.add(new HitCircle(width * 0.7, height * 0.4, 2.0, 3));
        
        // First slider - starts after circles, next elements wait for it to complete
        double slider1Start = 2.5;
        double slider1Duration = 0.65;  // Halved: 1.3 -> 0.65 seconds
        hitObjects.add(new Slider(
            width * 0.6, height * 0.5,   // Start position
            width * 0.3, height * 0.6,   // End position
            slider1Start,                 // Hit time
            slider1Duration,              // Duration
            4,                            // Combo number
            false                         // Not repeating
        ));
        
        // Next circles start AFTER slider completes
        double afterSlider1 = slider1Start + slider1Duration + 0.3;
        hitObjects.add(new HitCircle(width * 0.4, height * 0.7, afterSlider1, 5));
        hitObjects.add(new HitCircle(width * 0.6, height * 0.7, afterSlider1 + 0.5, 6));
        
        // Repeating slider - also halved
        double slider2Start = afterSlider1 + 1.0;
        double slider2Duration = 1.0;  // Halved: 2.0 -> 1.0 seconds for repeat
        hitObjects.add(new Slider(
            width * 0.25, height * 0.5,  // Start position
            width * 0.75, height * 0.5,  // End position
            slider2Start,                 // Hit time
            slider2Duration,              // Duration (halved)
            7,                            // Combo number
            true                          // Repeating
        ));
        
        // Final circles after second slider (removed combo 10)
        double afterSlider2 = slider2Start + slider2Duration + 0.3;
        hitObjects.add(new HitCircle(width * 0.5, height * 0.4, afterSlider2, 8));
        hitObjects.add(new HitCircle(width * 0.3, height * 0.6, afterSlider2 + 0.5, 9));
        // Removed the circle with combo 10
        
        // Update loop duration to accommodate all objects
        loopDuration = afterSlider2 + 1.5;
    }
    
    public void update(double deltaTime) {
        currentTime += deltaTime;
        
        // Update hit objects
        for (HitObject obj : hitObjects) {
            double prevResult = obj.getHitResult().ordinal();
            obj.update(currentTime);
            
            // Check if object was just hit
            if (prevResult == 0 && obj.isHit()) {
                onHitObjectHit(obj);
            }
        }
        
        // Update cursor position and trail
        updateCursor();
        
        // Remove inactive hit bursts
        activeHitBursts.removeIf(burst -> !burst.isActive(currentTime));
        
        // Loop animation
        if (currentTime >= loopDuration) {
            reset();
        }
    }
    
    private void onHitObjectHit(HitObject obj) {
        // Create hit burst
        Image[] frames = hitBurstFrames.get(obj.getHitResult());
        if (frames != null) {
            HitBurst burst = new HitBurst(
                obj.getHitResult(),
                obj.getX(),
                obj.getY(),
                currentTime,
                frames,
                lightingImage
            );
            activeHitBursts.add(burst);
        }
        
        // Update statistics
        updateStatistics(obj);
        
        // Update UI
        gameplayUI.onHit(obj.getHitResult());
        gameplayUI.setScore(score);
        gameplayUI.setCombo(combo);
        gameplayUI.setAccuracy(accuracy);
        
        logger.debug("Hit object hit with result: {} at time: {}", obj.getHitResult(), currentTime);
    }
    
    private void updateStatistics(HitObject obj) {
        HitObject.HitResult result = obj.getHitResult();
        
        // Update combo
        if (result != HitObject.HitResult.MISS) {
            combo++;
        } else {
            combo = 0;
        }
        
        // Update score
        score += obj.getScore() * Math.max(1, combo / 10);
        
        // Update accuracy
        totalHits++;
        switch (result) {
            case HIT_300: perfect300++; break;
            case HIT_100: good100++; break;
            case HIT_50: meh50++; break;
        }
        
        if (totalHits > 0) {
            accuracy = ((perfect300 * 300.0 + good100 * 100.0 + meh50 * 50.0) / (totalHits * 300.0)) * 100.0;
        }
    }
    
    private void updateCursor() {
        double cursorX = canvas.getWidth() / 2;
        double cursorY = canvas.getHeight() / 2;
        
        // Get previous cursor position for smooth interpolation
        if (!cursorTrailPoints.isEmpty()) {
            CursorTrailPoint lastPoint = cursorTrailPoints.getFirst();
            cursorX = lastPoint.x;
            cursorY = lastPoint.y;
        }
        
        // Find the current target (active slider or next object)
        HitObject currentTarget = null;
        HitObject nextTarget = null;
        
        for (HitObject obj : hitObjects) {
            // Check for active slider - cursor should stick to it during entire duration
            if (obj instanceof Slider) {
                Slider slider = (Slider) obj;
                if (slider.isActive(currentTime)) {
                    // Follow slider ball closely during active slide
                    double[] ballPos = slider.getSliderBallPosition(currentTime);
                    cursorX = smoothInterpolate(cursorX, ballPos[0], 0.25);  // Faster tracking for slider
                    cursorY = smoothInterpolate(cursorY, ballPos[1], 0.25);
                    currentTarget = slider;
                    break;
                }
            }
            
            // Find next unhit object
            if (!obj.isHit() && obj.isVisible(currentTime) && nextTarget == null) {
                double timeToHit = obj.getHitTime() - currentTime;
                if (timeToHit > 0 && timeToHit < 2.0) {  // Start moving earlier
                    nextTarget = obj;
                }
            }
        }
        
        // If not following slider, move to next object more slowly
        if (currentTarget == null && nextTarget != null) {
            double timeToHit = nextTarget.getHitTime() - currentTime;
            double moveSpeed = Math.min(1.0, Math.max(0.0, 1.0 - (timeToHit / 2.0)));
            moveSpeed = easeInOutCubic(moveSpeed);
            
            // Slower movement speed for more natural motion
            cursorX = smoothInterpolate(cursorX, nextTarget.getX(), moveSpeed * 0.05);
            cursorY = smoothInterpolate(cursorY, nextTarget.getY(), moveSpeed * 0.05);
        }
        
        // Update cursor trail
        cursorTrailPoints.addFirst(new CursorTrailPoint(cursorX, cursorY, currentTime));
        while (cursorTrailPoints.size() > MAX_TRAIL_POINTS) {
            cursorTrailPoints.removeLast();
        }
    }
    
    private double smoothInterpolate(double current, double target, double factor) {
        return current + (target - current) * factor;
    }
    
    private double easeInOutCubic(double t) {
        if (t < 0.5) {
            return 4 * t * t * t;
        } else {
            double p = 2 * t - 2;
            return 1 + p * p * p / 2;
        }
    }
    
    public void render() {
        // Clear canvas
        gc.setFill(Color.rgb(30, 30, 40));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        // Render layers in order (bottom to top)
        renderHitObjects();
        renderLightingEffects();
        renderHitBursts();
        renderCursorTrail();
        renderCursor();
        renderUI();
        renderBorder();
    }
    
    private void renderBorder() {
        gc.setStroke(Color.rgb(80, 80, 80));
        gc.setLineWidth(2);
        gc.strokeRect(1, 1, canvas.getWidth() - 2, canvas.getHeight() - 2);
    }
    
    private void renderHitObjects() {
        // First pass: render slider bodies (behind circles)
        for (HitObject obj : hitObjects) {
            if (obj instanceof Slider) {
                renderSliderBody((Slider) obj);
            }
        }
        
        // Second pass: render circles and slider heads
        for (HitObject obj : hitObjects) {
            if (!obj.isVisible(currentTime)) continue;
            
            double opacity = obj.getOpacity(currentTime);
            if (opacity <= 0) continue;
            
            gc.save();
            gc.setGlobalAlpha(opacity);
            
            if (obj instanceof Slider) {
                Slider slider = (Slider) obj;
                
                // Draw approach circle for slider head
                if (slider.isHeadVisible(currentTime) && approachCircle != null) {
                    double scale = slider.getApproachScale(currentTime);
                    if (scale > 1.0) {
                        drawCenteredImage(approachCircle, slider.getX(), slider.getY(), approachCircleScale * scale);
                    }
                }
                
                // Draw slider head circle
                if (slider.isHeadVisible(currentTime)) {
                    if (hitCircle != null) {
                        // Apply combo color tinting to slider head
                        javafx.scene.paint.Color comboColor = getComboColorForObject(slider);
                        drawCenteredImageWithTint(hitCircle, slider.getX(), slider.getY(), hitCircleScale, comboColor);
                    }
                    
                    if (hitCircleOverlay != null) {
                        drawCenteredImage(hitCircleOverlay, slider.getX(), slider.getY(), hitCircleScale);
                    }
                    
                    // Draw combo number on slider head
                    drawComboNumber(slider.getX(), slider.getY(), slider.getComboNumber());
                }
                
                // Draw slider ball
                renderSliderBall(slider);
                
            } else {
                // Regular hit circle
                // Draw approach circle
                if (!obj.isHit() && approachCircle != null) {
                    double scale = obj.getApproachScale(currentTime);
                    if (scale > 1.0) {
                        drawCenteredImage(approachCircle, obj.getX(), obj.getY(), approachCircleScale * scale);
                    }
                }
                
                // Draw hit circle (if not hit)
                if (!obj.isHit()) {
                    if (hitCircle != null) {
                        // Apply combo color tinting
                        javafx.scene.paint.Color comboColor = getComboColorForObject(obj);
                        drawCenteredImageWithTint(hitCircle, obj.getX(), obj.getY(), hitCircleScale, comboColor);
                    }
                    
                    if (hitCircleOverlay != null) {
                        drawCenteredImage(hitCircleOverlay, obj.getX(), obj.getY(), hitCircleScale);
                    }
                    
                    // Draw combo number
                    drawComboNumber(obj.getX(), obj.getY(), obj.getComboNumber());
                }
            }
            
            gc.restore();
        }
    }
    
    private void renderSliderBody(Slider slider) {
        if (!slider.isBodyVisible(currentTime)) return;
        
        gc.save();
        gc.setGlobalAlpha(slider.getOpacity(currentTime) * 0.9);
        
        // Calculate slider path
        double startX = slider.getX();
        double startY = slider.getY();
        double endX = slider.getEndX();
        double endY = slider.getEndY();
        
        // Draw slider track with proper styling
        // Outer border (darker)
        gc.setStroke(Color.rgb(100, 100, 120, 0.8));
        gc.setLineWidth(circleSize + 4);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.strokeLine(startX, startY, endX, endY);
        
        // Inner track (lighter)
        gc.setStroke(Color.rgb(180, 180, 200, 0.6));
        gc.setLineWidth(circleSize - 4);
        gc.strokeLine(startX, startY, endX, endY);
        
        // If we have sliderb texture, try to overlay it
        if (sliderBody != null) {
            // TODO: Implement texture tiling along path
            // For now, just use the colored lines above
        }
        
        // Draw end circle for non-repeating sliders
        if (!slider.isRepeating()) {
            gc.setFill(Color.rgb(150, 150, 170, 0.5));
            gc.fillOval(endX - circleSize/2, endY - circleSize/2, circleSize, circleSize);
        }
        
        // Draw reverse arrow if repeating (with proper rotation)
        if (slider.isRepeating()) {
            // Calculate angle for arrow to point back towards start
            double angle = Math.atan2(startY - endY, startX - endX);
            double arrowScale = hitCircleScale * 0.6;  // Consistent with other elements
            
            if (reverseArrow != null) {
                gc.save();
                gc.translate(endX, endY);
                gc.rotate(Math.toDegrees(angle));
                drawCenteredImage(reverseArrow, 0, 0, arrowScale);
                gc.restore();
            } else {
                // Fallback arrow pointing back
                gc.save();
                gc.translate(endX, endY);
                gc.rotate(Math.toDegrees(angle));
                gc.setFill(Color.WHITE);
                gc.fillPolygon(
                    new double[]{-10, 10, 10, -10},
                    new double[]{0, -5, 5, 0},
                    4
                );
                gc.restore();
            }
        }
        
        gc.restore();
    }
    
    private void renderSliderBall(Slider slider) {
        // Show ball during entire active slider duration
        if (!slider.isActive(currentTime)) return;
        
        double[] ballPos = slider.getSliderBallPosition(currentTime);
        
        gc.save();
        gc.setGlobalAlpha(slider.getOpacity(currentTime));
        
        // Draw follow circle with consistent scaling
        double followCircleScale = hitCircleScale * 1.2;  // Slightly larger than hit circle
        if (sliderFollowCircle != null) {
            drawCenteredImage(sliderFollowCircle, ballPos[0], ballPos[1], followCircleScale);
        } else {
            // Fallback follow circle
            gc.setStroke(Color.rgb(255, 255, 255, 0.3));
            gc.setLineWidth(2);
            double followSize = circleSize * 1.2;  // Consistent with hit circle size
            gc.strokeOval(ballPos[0] - followSize/2, ballPos[1] - followSize/2, followSize, followSize);
        }
        
        // Draw slider ball with consistent scaling
        double ballScale = hitCircleScale * 0.5;  // Half the size of hit circle
        if (sliderBall != null) {
            drawCenteredImage(sliderBall, ballPos[0], ballPos[1], ballScale);
        } else {
            // Fallback ball
            double ballSize = circleSize * 0.3;  // Proportional to circle size
            gc.setFill(Color.WHITE);
            gc.fillOval(ballPos[0] - ballSize, ballPos[1] - ballSize, ballSize * 2, ballSize * 2);
            gc.setStroke(Color.rgb(200, 200, 200));
            gc.setLineWidth(2);
            gc.strokeOval(ballPos[0] - ballSize, ballPos[1] - ballSize, ballSize * 2, ballSize * 2);
        }
        
        gc.restore();
    }
    
    private void renderLightingEffects() {
        for (HitBurst burst : activeHitBursts) {
            if (burst.shouldShowLighting(currentTime)) {
                gc.save();
                gc.setGlobalAlpha(burst.getLightingOpacity(currentTime));
                
                Image lighting = burst.getLightingImage();
                if (lighting != null) {
                    // Consistent scaling for lighting - based on hit circle size
                    double baseScale = hitCircleScale * burst.getLightingScale(currentTime);
                    drawCenteredImage(lighting, burst.getX(), burst.getY(), baseScale);
                }
                
                gc.restore();
            }
        }
    }
    
    private void renderHitBursts() {
        for (HitBurst burst : activeHitBursts) {
            Image frame = burst.getCurrentFrame(currentTime);
            if (frame != null) {
                gc.save();
                gc.setGlobalAlpha(burst.getOpacity(currentTime));
                
                double y = burst.getY() + burst.getYOffset(currentTime);
                // Use consistent scaling for hit bursts - smaller than hit circles
                double burstScale = hitCircleScale * 0.7;  // 70% of hit circle size
                drawCenteredImage(frame, burst.getX(), y, burstScale);
                
                gc.restore();
            }
        }
    }
    
    private void renderCursorTrail() {
        if (cursorTrail == null || cursorTrailPoints.isEmpty()) return;
        
        int index = 0;
        for (CursorTrailPoint point : cursorTrailPoints) {
            double age = currentTime - point.time;
            double opacity = Math.max(0, 1.0 - (age * 5));  // Fade over 0.2 seconds
            
            if (opacity > 0) {
                gc.save();
                gc.setGlobalAlpha(opacity * 0.5);  // Trail is semi-transparent
                drawCenteredImage(cursorTrail, point.x, point.y, 0.5 * canvasScale);
                gc.restore();
            }
            
            index++;
        }
    }
    
    private void renderCursor() {
        if (!cursorTrailPoints.isEmpty()) {
            CursorTrailPoint current = cursorTrailPoints.getFirst();
            
            if (cursor != null) {
                drawCenteredImage(cursor, current.x, current.y, 0.5 * canvasScale);
            } else {
                // Fallback cursor
                double cursorSize = 8 * canvasScale;
                gc.setFill(Color.WHITE);
                gc.fillOval(current.x - cursorSize, current.y - cursorSize, cursorSize * 2, cursorSize * 2);
            }
        }
    }
    
    private void renderUI() {
        // Use the GameplayUI system for rendering
        gameplayUI.render(canvas.getWidth(), canvas.getHeight());
    }
    
    private void drawComboNumber(double x, double y, int number) {
        if (number < 1 || number > 9) return;
        
        Image numberImage = defaultNumbers[number];
        if (numberImage != null) {
            drawCenteredImage(numberImage, x, y, 0.5 * canvasScale);
        }
    }
    
    private void drawCenteredImage(Image image, double x, double y, double scale) {
        if (image == null) return;
        
        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;
        gc.drawImage(image, x - width/2, y - height/2, width, height);
    }
    
    private void drawCenteredImageWithTint(Image image, double x, double y, double scale, javafx.scene.paint.Color tint) {
        if (image == null) return;
        
        gc.save();
        
        // Set blend mode for tinting
        // Note: JavaFX doesn't have a direct multiply blend mode for tinting
        // We'll use a workaround with opacity and fill
        
        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;
        
        // Draw the base image
        gc.drawImage(image, x - width/2, y - height/2, width, height);
        
        // Apply color overlay with multiply-like effect
        gc.setGlobalAlpha(0.7);  // Adjust for tint strength
        gc.setFill(tint);
        
        // Create a clipping region for the circle shape
        gc.save();
        gc.beginPath();
        gc.arc(x, y, width/2, width/2, 0, 360);
        gc.closePath();
        gc.clip();
        
        // Fill with tint color
        gc.setGlobalBlendMode(javafx.scene.effect.BlendMode.MULTIPLY);
        gc.fillRect(x - width/2, y - height/2, width, height);
        gc.setGlobalBlendMode(javafx.scene.effect.BlendMode.SRC_OVER);
        
        gc.restore();
        gc.restore();
    }
    
    private javafx.scene.paint.Color getComboColorForObject(HitObject obj) {
        if (comboColors.isEmpty()) {
            return javafx.scene.paint.Color.WHITE;
        }
        
        // In osu!, colors change when starting a new combo
        // For our preview, we'll change colors every few objects
        // and after sliders (which typically start new combos)
        
        boolean isNewCombo = false;
        
        // Check if this is the first object or follows a slider
        int objIndex = hitObjects.indexOf(obj);
        if (objIndex == 0) {
            isNewCombo = true;
        } else if (objIndex > 0) {
            HitObject prevObj = hitObjects.get(objIndex - 1);
            if (prevObj instanceof Slider) {
                isNewCombo = true;
                currentComboColorIndex = (currentComboColorIndex + 1) % comboColors.size();
            }
        }
        
        // Also change color every 3-4 circles for visual variety
        if (obj.getComboNumber() == 1 || obj.getComboNumber() == 5) {
            currentComboColorIndex = (currentComboColorIndex + 1) % comboColors.size();
        }
        
        return comboColors.get(currentComboColorIndex);
    }
    
    public void reset() {
        currentTime = 0;
        combo = 0;
        score = 0;
        accuracy = 100.0;
        totalHits = 0;
        perfect300 = 0;
        good100 = 0;
        meh50 = 0;
        
        activeHitBursts.clear();
        cursorTrailPoints.clear();
        
        // Reset UI
        gameplayUI.reset();
        
        // Reset hit objects
        for (HitObject obj : hitObjects) {
            if (obj instanceof HitCircle) {
                ((HitCircle) obj).reset();
            } else if (obj instanceof Slider) {
                ((Slider) obj).reset();
            }
        }
        
        setupHitObjects();
    }
    
    // Inner class for cursor trail
    private static class CursorTrailPoint {
        final double x, y, time;
        
        CursorTrailPoint(double x, double y, double time) {
            this.x = x;
            this.y = y;
            this.time = time;
        }
    }
}