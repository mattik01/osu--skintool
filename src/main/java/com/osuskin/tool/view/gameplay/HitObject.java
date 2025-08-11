package com.osuskin.tool.view.gameplay;

/**
 * Base class for all hit objects in the preview animation.
 */
public abstract class HitObject {
    
    public enum HitResult {
        NONE,     // Not hit yet
        MISS,     // Missed (0 points)
        HIT_50,   // Meh hit (50 points)
        HIT_100,  // Good hit (100 points)  
        HIT_300   // Perfect hit (300 points)
    }
    
    protected final double x;
    protected final double y;
    protected final double appearTime;  // When approach circle appears
    protected final double hitTime;     // When to hit the object
    protected final int comboNumber;
    
    protected HitResult hitResult = HitResult.NONE;
    protected double hitAnimationTime = 0;
    
    // Constants
    protected static final double APPROACH_TIME = 0.8;  // Time for approach circle
    protected static final double HIT_WINDOW_300 = 0.05; // ±50ms for perfect
    protected static final double HIT_WINDOW_100 = 0.10; // ±100ms for good
    protected static final double HIT_WINDOW_50 = 0.15;  // ±150ms for meh
    
    public HitObject(double x, double y, double hitTime, int comboNumber) {
        this.x = x;
        this.y = y;
        this.hitTime = hitTime;
        this.appearTime = hitTime - APPROACH_TIME;
        this.comboNumber = comboNumber;
    }
    
    /**
     * Update the hit object based on current time.
     * Automatically determines hit result based on timing.
     */
    public void update(double currentTime) {
        if (hitResult == HitResult.NONE && currentTime >= hitTime) {
            // Vary hit results for more realistic preview
            // Pattern: mix of perfect, good, meh, and occasional miss
            int pattern = comboNumber % 5;
            
            switch (pattern) {
                case 0:  // Perfect hit
                case 2:
                    hitResult = HitResult.HIT_300;
                    break;
                case 1:  // Good hit
                    hitResult = HitResult.HIT_100;
                    break;
                case 3:  // Meh hit
                    hitResult = HitResult.HIT_50;
                    break;
                case 4:  // Miss
                    hitResult = HitResult.MISS;
                    break;
                default:
                    hitResult = HitResult.HIT_300;
            }
            
            hitAnimationTime = currentTime;
        }
    }
    
    /**
     * Check if the hit object should be visible at current time.
     */
    public boolean isVisible(double currentTime) {
        // Visible from approach time until shortly after hit
        return currentTime >= appearTime && currentTime <= hitTime + 0.5;
    }
    
    /**
     * Check if hit object has been hit.
     */
    public boolean isHit() {
        return hitResult != HitResult.NONE;
    }
    
    /**
     * Get the approach circle scale (2.0 = start, 1.0 = hit time).
     */
    public double getApproachScale(double currentTime) {
        if (currentTime < appearTime || isHit()) {
            return 0;
        }
        
        double progress = (currentTime - appearTime) / APPROACH_TIME;
        progress = Math.min(1.0, Math.max(0.0, progress));
        
        // Scale from 2.0 to 1.0
        return 2.0 - progress;
    }
    
    /**
     * Get opacity for fade in/out effects.
     */
    public double getOpacity(double currentTime) {
        if (currentTime < appearTime) {
            return 0;
        }
        
        if (isHit()) {
            // Fade out after hit
            double timeSinceHit = currentTime - hitAnimationTime;
            return Math.max(0, 1.0 - timeSinceHit * 2);
        }
        
        // Fade in during approach
        double fadeInTime = 0.1;
        double timeSinceAppear = currentTime - appearTime;
        
        if (timeSinceAppear < fadeInTime) {
            return timeSinceAppear / fadeInTime;
        }
        
        return 1.0;
    }
    
    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getHitTime() { return hitTime; }
    public double getAppearTime() { return appearTime; }
    public int getComboNumber() { return comboNumber; }
    public HitResult getHitResult() { return hitResult; }
    public double getHitAnimationTime() { return hitAnimationTime; }
    
    /**
     * Get the score value for this hit result.
     */
    public int getScore() {
        switch (hitResult) {
            case HIT_300: return 300;
            case HIT_100: return 100;
            case HIT_50: return 50;
            default: return 0;
        }
    }
}