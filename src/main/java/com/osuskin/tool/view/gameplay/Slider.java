package com.osuskin.tool.view.gameplay;

/**
 * Represents a slider in the preview animation.
 */
public class Slider extends HitObject {
    
    private final double endX;
    private final double endY;
    private final double duration;
    private final boolean isRepeating;
    
    private boolean sliderStarted = false;
    private boolean sliderCompleted = false;
    
    public Slider(double startX, double startY, double endX, double endY, 
                  double hitTime, double duration, int comboNumber, boolean isRepeating) {
        super(startX, startY, hitTime, comboNumber);
        this.endX = endX;
        this.endY = endY;
        this.duration = duration;
        this.isRepeating = isRepeating;
    }
    
    @Override
    public void update(double currentTime) {
        // Check if slider has started
        if (!sliderStarted && currentTime >= hitTime) {
            sliderStarted = true;
            // Don't mark as hit until completed
            // hitResult = HitResult.HIT_300;
            // hitAnimationTime = currentTime;
        }
        
        // Check if slider has completed
        if (!sliderCompleted && currentTime >= hitTime + duration) {
            sliderCompleted = true;
            // Mark as hit when completed
            hitResult = HitResult.HIT_300; // Assume perfect for preview
            hitAnimationTime = currentTime;
        }
    }
    
    @Override
    public boolean isVisible(double currentTime) {
        // Slider stays visible from approach until fade out after completion
        return currentTime >= appearTime && currentTime <= hitTime + duration + 0.5;
    }
    
    /**
     * Get the position of the slider ball at current time.
     */
    public double[] getSliderBallPosition(double currentTime) {
        if (currentTime < hitTime) {
            return new double[]{x, y};
        }
        
        if (currentTime > hitTime + duration) {
            // Keep at end position after completion
            if (isRepeating) {
                // Repeating sliders end at start position
                return new double[]{x, y};
            } else {
                return new double[]{endX, endY};
            }
        }
        
        double progress = (currentTime - hitTime) / duration;
        
        if (isRepeating) {
            // Ping-pong between start and end
            int repeat = (int)(progress * 2);
            progress = (progress * 2) % 1.0;
            
            if (repeat % 2 == 1) {
                // Going backwards
                progress = 1.0 - progress;
            }
        }
        
        double ballX = x + (endX - x) * progress;
        double ballY = y + (endY - y) * progress;
        
        return new double[]{ballX, ballY};
    }
    
    /**
     * Check if the slider head circle should be visible.
     */
    public boolean isHeadVisible(double currentTime) {
        return currentTime >= appearTime && !sliderStarted;
    }
    
    /**
     * Check if the slider body should be visible.
     */
    public boolean isBodyVisible(double currentTime) {
        // Body stays visible for full slider duration plus fade time
        boolean visible = currentTime >= appearTime && currentTime <= hitTime + duration + 0.5;
        
        // Debug: log visibility issues
        if (!visible && currentTime >= hitTime && currentTime <= hitTime + duration) {
            System.out.println("WARNING: Slider body invisible during active time! Current: " + currentTime + 
                             ", Hit: " + hitTime + ", Duration: " + duration);
        }
        
        return visible;
    }
    
    /**
     * Check if slider is currently active (being slid).
     */
    public boolean isActive(double currentTime) {
        return currentTime >= hitTime && currentTime <= hitTime + duration;
    }
    
    @Override
    public double getOpacity(double currentTime) {
        if (currentTime < appearTime) {
            return 0;
        }
        
        // During slider duration, stay fully visible
        if (currentTime >= hitTime && currentTime <= hitTime + duration) {
            return 1.0;
        }
        
        // Fade in during approach
        if (currentTime < hitTime) {
            double fadeInTime = 0.1;
            double timeSinceAppear = currentTime - appearTime;
            if (timeSinceAppear < fadeInTime) {
                return timeSinceAppear / fadeInTime;
            }
            return 1.0;
        }
        
        // Fade out after completion
        double timeSinceComplete = currentTime - (hitTime + duration);
        if (timeSinceComplete > 0) {
            return Math.max(0, 1.0 - timeSinceComplete * 2);
        }
        
        return 1.0;
    }
    
    /**
     * Check if slider ball should be visible.
     */
    public boolean isBallVisible(double currentTime) {
        return sliderStarted && !sliderCompleted;
    }
    
    /**
     * Reset the slider for animation loop.
     */
    public void reset() {
        hitResult = HitResult.NONE;
        hitAnimationTime = 0;
        sliderStarted = false;
        sliderCompleted = false;
    }
    
    // Getters
    public double getEndX() { return endX; }
    public double getEndY() { return endY; }
    public double getDuration() { return duration; }
    public boolean isRepeating() { return isRepeating; }
    public boolean isStarted() { return sliderStarted; }
    public boolean isCompleted() { return sliderCompleted; }
}