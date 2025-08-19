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
    private com.osuskin.tool.model.Skin currentSkin;
    
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
    
    
    // UI system
    private GameplayUI gameplayUI;
    
    // Cursor trail
    private LinkedList<CursorTrailPoint> cursorTrailPoints = new LinkedList<>();
    private static final int MAX_TRAIL_POINTS = 20;
    
    // Cursor animation state
    private double cursorExpandScale = 1.0;  // Current expansion scale
    private double lastHitTime = -1.0;  // Time of last hit for expansion animation
    
    // Animation state
    private double currentTime = 0;
    private double loopDuration = 8.0;  // Total loop time
    
    // Rendering constants
    private static final double BASE_CIRCLE_SIZE = 80;  // Base size for fallback circles
    
    // Statistics
    private int combo = 0;
    private int score = 0;
    private double accuracy = 100.0;
    private int totalHits = 0;
    private int perfect300 = 0;
    private int good100 = 0;
    private int meh50 = 0;
    
    // Combo colors from skin
    private List<int[]> comboColors = new ArrayList<>();
    
    public GameplayRenderer(Canvas canvas, SkinElementLoader elementLoader) {
        this.canvas = canvas;
        this.elementLoader = elementLoader;
        this.gc = canvas.getGraphicsContext2D();
        this.gameplayUI = new GameplayUI(gc);
    }
    
    public void initialize() {
        // Get the current skin from elementLoader
        currentSkin = elementLoader.getCurrentSkin();
        
        // Get combo colors from skin
        if (currentSkin != null && currentSkin.getComboColors() != null) {
            comboColors = currentSkin.getComboColors();
        }
        
        loadElements();
        gameplayUI.loadElements(elementLoader);
        // No scaling for UI elements
        gameplayUI.setScale(1.0);
        setupHitObjects();
        logger.info("GameplayRenderer initialized with enhanced features");
    }
    
    /**
     * Handle canvas resize.
     */
    public void onCanvasResize() {
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
        
        // Load numbers with custom prefix support
        String hitCirclePrefix = "default";
        if (currentSkin != null && currentSkin.getHitCirclePrefix() != null) {
            hitCirclePrefix = currentSkin.getHitCirclePrefix();
        }
        
        for (int i = 0; i < 10; i++) {
            defaultNumbers[i] = elementLoader.loadImageWithPrefix(hitCirclePrefix, "-" + i);
            if (defaultNumbers[i] == null) {
                // Fallback to default if custom prefix not found
                defaultNumbers[i] = elementLoader.loadImage("default-" + i);
            }
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
    
    
    
    private void setupHitObjects() {
        hitObjects.clear();
        
        // Always use native resolution for positioning
        // JavaFX will automatically scale when drawing to the smaller canvas
        double width = 1366;
        double height = 768;
        
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
        
        // Update UI animations
        gameplayUI.update(deltaTime);
        
        // Update hit objects
        for (HitObject obj : hitObjects) {
            double prevResult = obj.getHitResult().ordinal();
            obj.update(currentTime);
            
            // Check if object was just hit
            if (prevResult == 0 && obj.isHit()) {
                onHitObjectHit(obj);
            }
        }
        
        // Update cursor animation
        updateCursorAnimation();
        
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
        // Trigger cursor expansion animation if enabled
        if (currentSkin != null && currentSkin.getCursorExpand() && 
            obj.getHitResult() != HitObject.HitResult.MISS) {
            lastHitTime = currentTime;
            cursorExpandScale = 1.3;  // Expand to 130% size
        }
        
        // Create hit burst
        Image[] frames = hitBurstFrames.get(obj.getHitResult());
        if (frames != null) {
            // Get animation framerate from skin
            int animationFramerate = currentSkin != null ? 
                currentSkin.getAnimationFramerate() : -1;
            
            HitBurst burst = new HitBurst(
                obj.getHitResult(),
                obj.getX(),
                obj.getY(),
                currentTime,
                frames,
                lightingImage,
                animationFramerate
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
    
    private void updateCursorAnimation() {
        // Animate cursor expansion scale back to normal
        if (cursorExpandScale > 1.0) {
            double timeSinceHit = currentTime - lastHitTime;
            // Return to normal size over 0.1 seconds
            if (timeSinceHit < 0.1) {
                // Smooth interpolation from 1.3 to 1.0
                cursorExpandScale = 1.3 - (timeSinceHit / 0.1) * 0.3;
            } else {
                cursorExpandScale = 1.0;
            }
        }
    }
    
    private void updateCursor() {
        // Use native resolution for cursor positioning
        double cursorX = 1366 / 2;
        double cursorY = 768 / 2;
        
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
                    // Use higher interpolation factor for sliders to track closely
                    double sliderTrackFactor = 0.3;
                    cursorX = cursorX + (ballPos[0] - cursorX) * sliderTrackFactor;
                    cursorY = cursorY + (ballPos[1] - cursorY) * sliderTrackFactor;
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
            
            // Calculate distance to target
            double distanceX = nextTarget.getX() - cursorX;
            double distanceY = nextTarget.getY() - cursorY;
            double distance = Math.sqrt(distanceX * distanceX + distanceY * distanceY);
            
            // If we're very close to the target position, just stay there
            if (distance < 3.0) {
                cursorX = nextTarget.getX();
                cursorY = nextTarget.getY();
            } else {
                // Use smooth easing based on time remaining
                double moveSpeed = Math.min(1.0, Math.max(0.0, 1.0 - (timeToHit / 2.0)));
                moveSpeed = easeInOutCubic(moveSpeed);
                
                // Scale the interpolation factor based on distance
                // For far objects, use a higher base factor to ensure we reach them
                double baseFactor = 0.05;
                double distanceBoost = Math.min(0.15, distance / 500.0); // Add up to 0.15 for far objects
                double finalFactor = (baseFactor + distanceBoost) * moveSpeed;
                
                // If we're running out of time, increase the factor to ensure arrival
                if (timeToHit < 0.5 && distance > 50) {
                    finalFactor = Math.max(finalFactor, 0.2);
                }
                
                cursorX = smoothInterpolate(cursorX, nextTarget.getX(), finalFactor);
                cursorY = smoothInterpolate(cursorY, nextTarget.getY(), finalFactor);
            }
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
        // Save the current transform
        gc.save();
        
        // Clear canvas at actual size first
        gc.setFill(Color.rgb(30, 30, 40));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        // Apply scaling transform to render at native resolution
        // but display at canvas size
        double scaleX = canvas.getWidth() / 1366.0;
        double scaleY = canvas.getHeight() / 768.0;
        gc.scale(scaleX, scaleY);
        
        // Now render everything at native resolution coordinates
        // The scale transform will automatically downscale to fit
        renderHitObjects();
        renderLightingEffects();
        renderHitBursts();
        renderCursorTrail();
        renderCursor();
        renderUI();
        renderBorder();
        
        // Restore the transform
        gc.restore();
    }
    
    private void renderBorder() {
        // Render border at native resolution
        gc.setStroke(Color.rgb(80, 80, 80));
        gc.setLineWidth(2);
        gc.strokeRect(1, 1, 1366 - 2, 768 - 2);
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
                        drawCenteredImage(approachCircle, slider.getX(), slider.getY(), scale);
                    }
                }
                
                // Draw slider head circle
                if (slider.isHeadVisible(currentTime)) {
                    // Always draw base hitcircle first
                    if (hitCircle != null) {
                        drawCenteredImage(hitCircle, slider.getX(), slider.getY(), 1.0);
                    }
                    
                    // Check layering order from skin.ini
                    boolean overlayAboveNumber = currentSkin != null ? 
                        currentSkin.getHitCircleOverlayAboveNumber() : true;
                    
                    if (!overlayAboveNumber) {
                        // Draw overlay before number
                        if (hitCircleOverlay != null) {
                            drawCenteredImage(hitCircleOverlay, slider.getX(), slider.getY(), 1.0);
                        }
                    }
                    
                    // Draw combo number on slider head
                    drawComboNumber(slider.getX(), slider.getY(), slider.getComboNumber());
                    
                    if (overlayAboveNumber) {
                        // Draw overlay after number (default)
                        if (hitCircleOverlay != null) {
                            drawCenteredImage(hitCircleOverlay, slider.getX(), slider.getY(), 1.0);
                        }
                    }
                }
                
                // Draw slider ball
                renderSliderBall(slider);
                
            } else {
                // Regular hit circle
                // Draw approach circle
                if (!obj.isHit() && approachCircle != null) {
                    double scale = obj.getApproachScale(currentTime);
                    if (scale > 1.0) {
                        drawCenteredImage(approachCircle, obj.getX(), obj.getY(), scale);
                    }
                }
                
                // Draw hit circle (if not hit)
                if (!obj.isHit()) {
                    // Always draw base hitcircle first
                    if (hitCircle != null) {
                        drawCenteredImage(hitCircle, obj.getX(), obj.getY(), 1.0);
                    }
                    
                    // Check layering order from skin.ini
                    boolean overlayAboveNumber = currentSkin != null ? 
                        currentSkin.getHitCircleOverlayAboveNumber() : true;
                    
                    if (!overlayAboveNumber) {
                        // Draw overlay before number
                        if (hitCircleOverlay != null) {
                            drawCenteredImage(hitCircleOverlay, obj.getX(), obj.getY(), 1.0);
                        }
                    }
                    
                    // Draw combo number
                    drawComboNumber(obj.getX(), obj.getY(), obj.getComboNumber());
                    
                    if (overlayAboveNumber) {
                        // Draw overlay after number (default)
                        if (hitCircleOverlay != null) {
                            drawCenteredImage(hitCircleOverlay, obj.getX(), obj.getY(), 1.0);
                        }
                    }
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
        
        // Determine slider track color
        Color trackColor = null;
        if (currentSkin != null && currentSkin.getSliderTrackOverride() != null) {
            // Use override color from skin.ini
            int[] rgb = currentSkin.getSliderTrackOverride();
            trackColor = Color.rgb(rgb[0], rgb[1], rgb[2], 0.6);
        } else if (!comboColors.isEmpty()) {
            // Use combo color
            int comboIndex = (slider.getComboNumber() - 1) % comboColors.size();
            if (comboIndex >= 0 && comboIndex < comboColors.size()) {
                int[] rgb = comboColors.get(comboIndex);
                trackColor = Color.rgb(rgb[0], rgb[1], rgb[2], 0.6);
            } else {
                trackColor = Color.rgb(180, 180, 200, 0.6);
            }
        } else {
            trackColor = Color.rgb(180, 180, 200, 0.6);
        }
        
        // Determine border color
        Color borderColor = Color.rgb(255, 255, 255, 0.8);  // Default white
        if (currentSkin != null && currentSkin.getSliderBorderColor() != null) {
            int[] rgb = currentSkin.getSliderBorderColor();
            borderColor = Color.rgb(rgb[0], rgb[1], rgb[2], 0.8);
        }
        
        // Draw slider track with proper styling
        // Outer border
        gc.setStroke(borderColor);
        gc.setLineWidth(BASE_CIRCLE_SIZE + 4);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.strokeLine(startX, startY, endX, endY);
        
        // Inner track with color
        gc.setStroke(trackColor);
        gc.setLineWidth(BASE_CIRCLE_SIZE - 4);
        gc.strokeLine(startX, startY, endX, endY);
        
        // If we have sliderb texture, overlay it
        if (sliderBody != null) {
            // TODO: Implement texture tiling along path
            // For now, just use the colored lines above
        }
        
        // Draw end circle for non-repeating sliders
        if (!slider.isRepeating()) {
            gc.setFill(Color.rgb(150, 150, 170, 0.5));
            gc.fillOval(endX - BASE_CIRCLE_SIZE/2, endY - BASE_CIRCLE_SIZE/2, BASE_CIRCLE_SIZE, BASE_CIRCLE_SIZE);
        }
        
        // Draw reverse arrow if repeating (with proper rotation)
        if (slider.isRepeating()) {
            // Calculate angle for arrow to point back towards start
            double angle = Math.atan2(startY - endY, startX - endX);
            double arrowScale = 1.0;  // No scaling
            
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
        if (sliderFollowCircle != null) {
            drawCenteredImage(sliderFollowCircle, ballPos[0], ballPos[1], 1.0);
        } else {
            // Fallback follow circle
            gc.setStroke(Color.rgb(255, 255, 255, 0.3));
            gc.setLineWidth(2);
            double followSize = BASE_CIRCLE_SIZE * 1.2;  // Consistent with hit circle size
            gc.strokeOval(ballPos[0] - followSize/2, ballPos[1] - followSize/2, followSize, followSize);
        }
        
        // Check if we need to flip the slider ball
        boolean shouldFlip = currentSkin != null && currentSkin.getSliderBallFlip() && 
                            slider.isSliderBallReversed(currentTime);
        
        // Apply combo color tint if enabled
        boolean shouldTint = currentSkin != null && currentSkin.getAllowSliderBallTint();
        
        // Draw slider ball with flip and tint
        if (sliderBall != null) {
            gc.save();
            
            if (shouldFlip) {
                // Flip horizontally around ball position
                gc.translate(ballPos[0], ballPos[1]);
                gc.scale(-1, 1);
                gc.translate(-ballPos[0], -ballPos[1]);
            }
            
            drawCenteredImage(sliderBall, ballPos[0], ballPos[1], 1.0);
            
            // Apply tint overlay if needed
            if (shouldTint) {
                // Get current combo color
                int comboIndex = (slider.getComboNumber() - 1) % comboColors.size();
                if (comboIndex >= 0 && comboIndex < comboColors.size()) {
                    int[] rgb = comboColors.get(comboIndex);
                    gc.setGlobalAlpha(0.5 * slider.getOpacity(currentTime)); // Semi-transparent tint
                    gc.setFill(Color.rgb(rgb[0], rgb[1], rgb[2]));
                    double ballSize = sliderBall.getWidth() / 2;
                    gc.fillOval(ballPos[0] - ballSize, ballPos[1] - ballSize, ballSize * 2, ballSize * 2);
                }
            }
            
            gc.restore();
        } else {
            // Fallback ball with tint support
            double ballSize = BASE_CIRCLE_SIZE * 0.3;
            
            if (shouldTint && currentSkin != null) {
                // Use combo color or SliderBall color from skin.ini
                int[] color = currentSkin.getSliderBallColor();
                if (color != null) {
                    gc.setFill(Color.rgb(color[0], color[1], color[2]));
                } else {
                    // Use combo color
                    int comboIndex = (slider.getComboNumber() - 1) % comboColors.size();
                    if (comboIndex >= 0 && comboIndex < comboColors.size()) {
                        int[] rgb = comboColors.get(comboIndex);
                        gc.setFill(Color.rgb(rgb[0], rgb[1], rgb[2]));
                    } else {
                        gc.setFill(Color.WHITE);
                    }
                }
            } else {
                gc.setFill(Color.WHITE);
            }
            
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
                    double baseScale = burst.getLightingScale(currentTime);
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
                drawCenteredImage(frame, burst.getX(), y, 1.0);
                
                gc.restore();
            }
        }
    }
    
    private void renderCursorTrail() {
        if (cursorTrail == null || cursorTrailPoints.isEmpty()) return;
        
        // Check CursorCentre property for trail positioning
        boolean cursorCentre = currentSkin != null ? currentSkin.getCursorCentre() : true;
        
        int index = 0;
        for (CursorTrailPoint point : cursorTrailPoints) {
            double age = currentTime - point.time;
            double opacity = Math.max(0, 1.0 - (age * 5));  // Fade over 0.2 seconds
            
            if (opacity > 0) {
                gc.save();
                gc.setGlobalAlpha(opacity * 0.5);  // Trail is semi-transparent
                
                if (cursorCentre) {
                    drawCenteredImage(cursorTrail, point.x, point.y, 1.0);
                } else {
                    // Top-left positioning for trail
                    gc.drawImage(cursorTrail, point.x, point.y);
                }
                
                gc.restore();
            }
            
            index++;
        }
    }
    
    private void renderCursor() {
        if (!cursorTrailPoints.isEmpty()) {
            CursorTrailPoint current = cursorTrailPoints.getFirst();
            
            if (cursor != null) {
                // Check CursorCentre property from skin.ini
                boolean cursorCentre = currentSkin != null ? currentSkin.getCursorCentre() : true;
                
                // Apply expansion scale
                double scale = cursorExpandScale;
                
                if (cursorCentre) {
                    // Center the cursor with scaling
                    drawCenteredImage(cursor, current.x, current.y, scale);
                } else {
                    // Top-left corner positioning with scaling
                    double width = cursor.getWidth() * scale;
                    double height = cursor.getHeight() * scale;
                    gc.drawImage(cursor, current.x, current.y, width, height);
                }
            } else {
                // Fallback cursor (always centered) with scaling
                double cursorSize = 8 * cursorExpandScale;
                gc.setFill(Color.WHITE);
                gc.fillOval(current.x - cursorSize, current.y - cursorSize, cursorSize * 2, cursorSize * 2);
            }
        }
    }
    
    private void renderUI() {
        // Use the GameplayUI system for rendering at native resolution
        gameplayUI.render(1366, 768);
    }
    
    private void drawComboNumber(double x, double y, int number) {
        if (number < 1 || number > 9) return;
        
        Image numberImage = defaultNumbers[number];
        if (numberImage != null) {
            drawCenteredImage(numberImage, x, y, 1.0);
        }
    }
    
    private void drawCenteredImage(Image image, double x, double y, double scale) {
        if (image == null) return;
        
        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;
        gc.drawImage(image, x - width/2, y - height/2, width, height);
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
        cursorExpandScale = 1.0;
        lastHitTime = -1.0;
        
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