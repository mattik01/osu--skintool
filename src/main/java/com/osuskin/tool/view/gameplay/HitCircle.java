package com.osuskin.tool.view.gameplay;

/**
 * Represents a hit circle in the preview animation.
 */
public class HitCircle extends HitObject {
    
    public HitCircle(double x, double y, double hitTime, int comboNumber) {
        super(x, y, hitTime, comboNumber);
    }
    
    /**
     * Check if the main circle should be visible (not just approach circle).
     * Circle appears when approach circle starts.
     */
    public boolean isCircleVisible(double currentTime) {
        if (currentTime < appearTime) {
            return false;
        }
        
        if (isHit()) {
            // Hide immediately when hit (only show hit burst)
            return false;
        }
        
        return currentTime <= hitTime + 0.1;
    }
    
    /**
     * Reset the hit circle for animation loop.
     */
    public void reset() {
        hitResult = HitResult.NONE;
        hitAnimationTime = 0;
    }
}