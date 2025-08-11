package com.osuskin.tool.view.gameplay;

import javafx.scene.image.Image;

/**
 * Manages hit burst animations for different hit results.
 * Supports both static and animated hit bursts.
 */
public class HitBurst {
    
    private final HitObject.HitResult result;
    private final double x;
    private final double y;
    private final double startTime;
    
    private Image[] frames;
    private Image lightingImage;
    
    private static final double ANIMATION_DURATION = 0.7;  // Total duration
    private static final double FRAME_DURATION = 0.05;     // Duration per frame
    private static final double RISE_DISTANCE = 20;       // Pixels to rise during animation
    private static final double LIGHTING_DURATION = 0.4;   // Longer lighting (was 0.2)
    
    public HitBurst(HitObject.HitResult result, double x, double y, double startTime, 
                    Image[] frames, Image lightingImage) {
        this.result = result;
        this.x = x;
        this.y = y;
        this.startTime = startTime;
        this.frames = frames;
        this.lightingImage = lightingImage;
    }
    
    /**
     * Check if the burst animation is still active.
     */
    public boolean isActive(double currentTime) {
        return currentTime - startTime < ANIMATION_DURATION;
    }
    
    /**
     * Get the current animation frame index.
     */
    public int getFrameIndex(double currentTime) {
        if (frames == null || frames.length == 0) {
            return -1;
        }
        
        double elapsed = currentTime - startTime;
        int frameIndex = (int)(elapsed / FRAME_DURATION);
        
        // Loop animation or stay on last frame
        if (frameIndex >= frames.length) {
            frameIndex = frames.length - 1;
        }
        
        return frameIndex;
    }
    
    /**
     * Get the current frame image.
     */
    public Image getCurrentFrame(double currentTime) {
        int index = getFrameIndex(currentTime);
        if (index >= 0 && index < frames.length) {
            return frames[index];
        }
        return null;
    }
    
    /**
     * Get opacity for the burst animation.
     */
    public double getOpacity(double currentTime) {
        double elapsed = currentTime - startTime;
        double progress = elapsed / ANIMATION_DURATION;
        
        if (progress >= 1.0) {
            return 0;
        }
        
        // Fade out in the last 30% of the animation
        if (progress > 0.7) {
            return 1.0 - ((progress - 0.7) / 0.3);
        }
        
        return 1.0;
    }
    
    /**
     * Get Y offset for rising animation.
     */
    public double getYOffset(double currentTime) {
        double elapsed = currentTime - startTime;
        double progress = Math.min(1.0, elapsed / ANIMATION_DURATION);
        
        // Ease out cubic for smooth deceleration
        progress = 1.0 - Math.pow(1.0 - progress, 3);
        
        return -RISE_DISTANCE * progress;
    }
    
    /**
     * Check if lighting effect should be shown.
     */
    public boolean shouldShowLighting(double currentTime) {
        if (lightingImage == null) {
            return false;
        }
        
        double elapsed = currentTime - startTime;
        return elapsed < LIGHTING_DURATION;
    }
    
    /**
     * Get lighting opacity.
     */
    public double getLightingOpacity(double currentTime) {
        if (!shouldShowLighting(currentTime)) {
            return 0;
        }
        
        double elapsed = currentTime - startTime;
        double progress = elapsed / LIGHTING_DURATION;
        
        // Quick fade out
        return 1.0 - progress;
    }
    
    /**
     * Get lighting scale for pulse effect.
     */
    public double getLightingScale(double currentTime) {
        if (!shouldShowLighting(currentTime)) {
            return 1.0;
        }
        
        double elapsed = currentTime - startTime;
        double progress = elapsed / LIGHTING_DURATION;
        
        // Smaller scale: start at 0.6, expand to 0.9 (was 0.8 to 1.2)
        return 0.6 + (0.3 * progress);
    }
    
    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public HitObject.HitResult getResult() { return result; }
    public Image getLightingImage() { return lightingImage; }
}