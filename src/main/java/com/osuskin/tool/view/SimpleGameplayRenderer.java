package com.osuskin.tool.view;

import com.osuskin.tool.service.SkinElementLoader;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplified gameplay renderer that displays skin elements in a repeating pattern.
 * Shows hit circles with proper layering as they would appear in osu!
 */
public class SimpleGameplayRenderer {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleGameplayRenderer.class);
    
    private final Canvas canvas;
    private final SkinElementLoader elementLoader;
    private final GraphicsContext gc;
    
    // Loaded skin elements
    private Image hitCircle;
    private Image hitCircleOverlay;
    private Image approachCircle;
    private Image cursor;
    private Image cursorTrail;
    private Image[] defaultNumbers = new Image[10];
    
    // Animation state
    private double animationTime = 0;
    private List<AnimatedHitCircle> hitCircles = new ArrayList<>();
    
    // Constants
    private static final double CIRCLE_SIZE = 64; // Base circle size for gameplay
    private static final double REFERENCE_SIZE = 128; // Reference size for scaling calculations
    private static final double APPROACH_TIME = 0.8; // Time for approach circle animation
    private static final double FADE_TIME = 0.3; // Time for fade out after hit
    
    // Calculated scales for consistent rendering
    private double hitCircleScale = 1.0;
    private double overlayScale = 1.0;
    private double approachCircleScale = 1.0;
    
    public SimpleGameplayRenderer(Canvas canvas, SkinElementLoader elementLoader) {
        this.canvas = canvas;
        this.elementLoader = elementLoader;
        this.gc = canvas.getGraphicsContext2D();
    }
    
    public void initialize() {
        loadElements();
        setupHitCircles();
        logger.info("SimpleGameplayRenderer initialized");
    }
    
    private void loadElements() {
        // Load hit circle elements
        hitCircle = elementLoader.loadImage("hitcircle");
        hitCircleOverlay = elementLoader.loadImage("hitcircleoverlay");
        approachCircle = elementLoader.loadImage("approachcircle");
        
        // Calculate proper scales based on actual image sizes
        calculateElementScales();
        
        // Load cursor
        cursor = elementLoader.loadImage("cursor");
        cursorTrail = elementLoader.loadImage("cursortrail");
        
        // Load numbers
        for (int i = 0; i < 10; i++) {
            defaultNumbers[i] = elementLoader.loadImage("default-" + i);
        }
        
        logger.debug("Loaded elements - hitcircle: {}, overlay: {}, approach: {}", 
                    hitCircle != null, hitCircleOverlay != null, approachCircle != null);
    }
    
    private void calculateElementScales() {
        // Base scale on hitcircle size
        if (hitCircle != null) {
            // Calculate how much to scale hitcircle to reach our target size
            double hitCircleSize = Math.max(hitCircle.getWidth(), hitCircle.getHeight());
            hitCircleScale = CIRCLE_SIZE / hitCircleSize;
            
            // Overlay should use the same scale regardless of its actual size
            // This ensures overlay always matches hitcircle
            overlayScale = hitCircleScale;
            if (hitCircleOverlay != null) {
                // If overlay is a different size, we still use hitcircle's scale
                double overlaySize = Math.max(hitCircleOverlay.getWidth(), hitCircleOverlay.getHeight());
                // Adjust if overlay needs to match hitcircle's rendered size
                overlayScale = (CIRCLE_SIZE / overlaySize) * (hitCircleSize / REFERENCE_SIZE);
            }
            
            // Approach circle should be scaled relative to hitcircle
            approachCircleScale = hitCircleScale;
            if (approachCircle != null) {
                // Approach circle might have a different base size
                // but should render at the same size as hitcircle when fully shrunk
                double approachSize = Math.max(approachCircle.getWidth(), approachCircle.getHeight());
                approachCircleScale = CIRCLE_SIZE / approachSize;
            }
            
            logger.debug("Calculated scales - hitCircle: {}, overlay: {}, approach: {}",
                        hitCircleScale, overlayScale, approachCircleScale);
        }
    }
    
    private void setupHitCircles() {
        hitCircles.clear();
        
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        
        // Create a pattern of hit circles
        hitCircles.add(new AnimatedHitCircle(width * 0.25, height * 0.5, 0.0, 1));
        hitCircles.add(new AnimatedHitCircle(width * 0.5, height * 0.3, 0.5, 2));
        hitCircles.add(new AnimatedHitCircle(width * 0.75, height * 0.5, 1.0, 3));
        hitCircles.add(new AnimatedHitCircle(width * 0.5, height * 0.7, 1.5, 4));
        hitCircles.add(new AnimatedHitCircle(width * 0.25, height * 0.3, 2.0, 1));
        hitCircles.add(new AnimatedHitCircle(width * 0.75, height * 0.7, 2.5, 2));
    }
    
    public void update(double deltaTime) {
        animationTime += deltaTime;
        
        // Update hit circles
        for (AnimatedHitCircle circle : hitCircles) {
            circle.update(animationTime);
        }
        
        // Reset animation after a certain time
        if (animationTime > 4.0) {
            animationTime = 0;
            for (AnimatedHitCircle circle : hitCircles) {
                circle.reset();
            }
        }
    }
    
    public void render() {
        // Clear canvas with dark background
        gc.setFill(Color.rgb(30, 30, 40));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        // Draw all hit circles
        for (AnimatedHitCircle circle : hitCircles) {
            renderHitCircle(circle);
        }
        
        // Draw cursor (if available)
        renderCursor();
    }
    
    private void renderHitCircle(AnimatedHitCircle circle) {
        if (!circle.isVisible()) return;
        
        double alpha = circle.getAlpha();
        if (alpha <= 0) return;
        
        gc.save();
        gc.setGlobalAlpha(alpha);
        
        // Draw approach circle (if before hit time)
        if (!circle.isHit() && approachCircle != null) {
            double approachProgress = circle.getApproachScale();
            if (approachProgress > 1.0) {
                // Apply the base approach scale and multiply by progress
                drawCenteredImage(approachCircle, circle.x, circle.y, approachCircleScale * approachProgress);
            }
        }
        
        // Draw main hit circle (if not hit yet)
        if (!circle.isHit()) {
            // Draw hitcircle base
            if (hitCircle != null) {
                drawCenteredImage(hitCircle, circle.x, circle.y, hitCircleScale);
            } else {
                // Fallback circle if image not found
                gc.setFill(Color.rgb(100, 150, 200));
                gc.fillOval(circle.x - CIRCLE_SIZE/2, circle.y - CIRCLE_SIZE/2, CIRCLE_SIZE, CIRCLE_SIZE);
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(4);
                gc.strokeOval(circle.x - CIRCLE_SIZE/2, circle.y - CIRCLE_SIZE/2, CIRCLE_SIZE, CIRCLE_SIZE);
            }
            
            // Draw overlay with same scale as hitcircle to ensure they match
            if (hitCircleOverlay != null) {
                drawCenteredImage(hitCircleOverlay, circle.x, circle.y, hitCircleScale);
            }
            
            // Draw combo number
            drawComboNumber(circle.x, circle.y, circle.comboNumber);
        } else {
            // Draw hit burst effect
            double burstScale = circle.getBurstScale();
            gc.setFill(Color.rgb(150, 200, 255, alpha * 0.5));
            double burstSize = CIRCLE_SIZE * burstScale;
            gc.fillOval(circle.x - burstSize/2, circle.y - burstSize/2, burstSize, burstSize);
        }
        
        gc.restore();
    }
    
    private void drawComboNumber(double x, double y, int number) {
        if (number < 1 || number > 9) return;
        
        Image numberImage = defaultNumbers[number];
        if (numberImage != null) {
            drawCenteredImage(numberImage, x, y, 0.5);
        } else {
            // Fallback text rendering
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", 24));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf(number), x, y + 8);
        }
    }
    
    private void renderCursor() {
        // Simple cursor following a path
        double t = (animationTime % 3.0) / 3.0;
        double cursorX = canvas.getWidth() * (0.2 + t * 0.6);
        double cursorY = canvas.getHeight() * 0.5 + Math.sin(t * Math.PI * 2) * 100;
        
        if (cursor != null) {
            drawCenteredImage(cursor, cursorX, cursorY, 0.5);
        } else {
            // Fallback cursor
            gc.setFill(Color.WHITE);
            gc.fillOval(cursorX - 8, cursorY - 8, 16, 16);
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);
            gc.strokeOval(cursorX - 8, cursorY - 8, 16, 16);
        }
    }
    
    private void drawCenteredImage(Image image, double x, double y, double scale) {
        if (image == null) return;
        
        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;
        
        // Always center the image regardless of its size
        gc.drawImage(image, x - width/2, y - height/2, width, height);
    }
    
    public void reset() {
        animationTime = 0;
        setupHitCircles();
    }
    
    // Inner class for animated hit circles
    private class AnimatedHitCircle {
        final double x, y;
        final double appearTime;
        final int comboNumber;
        private final double hitTime;
        private boolean hit = false;
        private double hitAnimationTime = 0;
        
        AnimatedHitCircle(double x, double y, double appearTime, int comboNumber) {
            this.x = x;
            this.y = y;
            this.appearTime = appearTime;
            this.comboNumber = comboNumber;
            this.hitTime = appearTime + APPROACH_TIME;
        }
        
        void update(double currentTime) {
            if (!hit && currentTime >= hitTime) {
                hit = true;
                hitAnimationTime = currentTime;
            }
        }
        
        void reset() {
            hit = false;
            hitAnimationTime = 0;
        }
        
        boolean isVisible() {
            return true; // Always visible in this simple version
        }
        
        boolean isHit() {
            return hit;
        }
        
        double getAlpha() {
            // Fade in/out logic
            return 1.0;
        }
        
        double getApproachScale() {
            // Calculate approach circle scale based on time
            if (hit) return 0;
            
            double timeUntilHit = hitTime - animationTime;
            if (timeUntilHit > APPROACH_TIME) return 0;
            if (timeUntilHit < 0) return 0;
            
            return 1.0 + (timeUntilHit / APPROACH_TIME) * 1.5;
        }
        
        double getBurstScale() {
            if (!hit) return 1.0;
            
            double timeSinceHit = animationTime - hitAnimationTime;
            return 1.0 + timeSinceHit * 2.0;
        }
    }
}