package com.osuskin.tool.view;

import com.osuskin.tool.service.SkinElementLoader;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Handles the animated gameplay preview showing skin elements in action.
 * Simulates osu! gameplay with circles, sliders, and cursor movement.
 */
public class GameplayAnimator {
    
    private static final Logger logger = LoggerFactory.getLogger(GameplayAnimator.class);
    
    private final Canvas gameplayCanvas;
    private final Canvas overlayCanvas;
    private final SkinElementLoader elementLoader;
    
    private GraphicsContext gc;
    private GraphicsContext overlayGc;
    
    // Animation state
    private double currentTime = 0;
    private double playbackSpeed = 1.0;
    private boolean isComplete = false;
    
    // Loaded elements
    private Image hitCircle;
    private Image hitCircleOverlay;
    private Image approachCircle;
    private Image cursor;
    private Image cursorTrail;
    private List<Image> sliderBodyFrames;
    private Image sliderFollowCircle;
    private Image reverseArrow;
    
    // Game objects for animation
    private List<GameObject> gameObjects = new ArrayList<>();
    private CursorAnimation cursorAnimation;
    
    // Animation parameters
    private static final double ANIMATION_DURATION = 10.0; // 10 seconds loop
    private static final double APPROACH_TIME = 0.8; // Time for approach circle
    private static final double HIT_WINDOW = 0.05; // Hit timing window
    
    public GameplayAnimator(Canvas gameplayCanvas, Canvas overlayCanvas, SkinElementLoader elementLoader) {
        this.gameplayCanvas = gameplayCanvas;
        this.overlayCanvas = overlayCanvas;
        this.elementLoader = elementLoader;
        
        this.gc = gameplayCanvas.getGraphicsContext2D();
        this.overlayGc = overlayCanvas.getGraphicsContext2D();
    }
    
    public void initialize() {
        loadElements();
        setupAnimation();
    }
    
    private void loadElements() {
        // Load hit circle elements
        hitCircle = elementLoader.loadImage("hitcircle");
        hitCircleOverlay = elementLoader.loadImage("hitcircleoverlay");
        approachCircle = elementLoader.loadImage("approachcircle");
        
        // Load cursor
        cursor = elementLoader.loadImage("cursor");
        cursorTrail = elementLoader.loadImage("cursortrail");
        
        // Load slider elements
        sliderBodyFrames = elementLoader.loadAnimation("sliderb");
        sliderFollowCircle = elementLoader.loadImage("sliderfollowcircle");
        reverseArrow = elementLoader.loadImage("reversearrow");
        
        // Initialize cursor animation
        cursorAnimation = new CursorAnimation(cursor, cursorTrail);
    }
    
    private void setupAnimation() {
        // Clear existing objects
        gameObjects.clear();
        
        double canvasWidth = gameplayCanvas.getWidth();
        double canvasHeight = gameplayCanvas.getHeight();
        
        // Create a sequence of hit objects
        // Pattern 1: Three hit circles in a triangle
        gameObjects.add(new HitCircleObject(canvasWidth * 0.3, canvasHeight * 0.3, 1.0));
        gameObjects.add(new HitCircleObject(canvasWidth * 0.7, canvasHeight * 0.3, 2.0));
        gameObjects.add(new HitCircleObject(canvasWidth * 0.5, canvasHeight * 0.6, 3.0));
        
        // Pattern 2: Slider
        gameObjects.add(new SliderObject(canvasWidth * 0.2, canvasHeight * 0.5, 
                                        canvasWidth * 0.8, canvasHeight * 0.5, 4.0, 2.0));
        
        // Pattern 3: More hit circles
        gameObjects.add(new HitCircleObject(canvasWidth * 0.4, canvasHeight * 0.7, 6.5));
        gameObjects.add(new HitCircleObject(canvasWidth * 0.6, canvasHeight * 0.7, 7.0));
        
        // Pattern 4: Vertical slider
        gameObjects.add(new SliderObject(canvasWidth * 0.5, canvasHeight * 0.2,
                                        canvasWidth * 0.5, canvasHeight * 0.8, 8.0, 1.5));
        
        // Setup cursor path
        List<CursorAnimation.PathPoint> cursorPath = new ArrayList<>();
        for (GameObject obj : gameObjects) {
            if (obj instanceof HitCircleObject) {
                HitCircleObject circle = (HitCircleObject) obj;
                cursorPath.add(new CursorAnimation.PathPoint(circle.x, circle.y, circle.hitTime));
            } else if (obj instanceof SliderObject) {
                SliderObject slider = (SliderObject) obj;
                cursorPath.add(new CursorAnimation.PathPoint(slider.x, slider.y, slider.startTime));
                cursorPath.add(new CursorAnimation.PathPoint(slider.endX, slider.endY, 
                                                            slider.startTime + slider.duration));
            }
        }
        cursorAnimation.setPath(cursorPath);
    }
    
    public void update(double deltaTime) {
        currentTime += deltaTime * playbackSpeed;
        
        // Loop animation
        if (currentTime >= ANIMATION_DURATION) {
            if (isComplete) {
                return;
            }
            currentTime = currentTime % ANIMATION_DURATION;
        }
        
        // Update cursor position
        cursorAnimation.update(currentTime);
        
        // Update game objects
        for (GameObject obj : gameObjects) {
            obj.update(currentTime);
        }
    }
    
    public void render() {
        // Clear canvases
        gc.clearRect(0, 0, gameplayCanvas.getWidth(), gameplayCanvas.getHeight());
        overlayGc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
        
        // Draw background gradient
        gc.setFill(Color.rgb(20, 20, 30));
        gc.fillRect(0, 0, gameplayCanvas.getWidth(), gameplayCanvas.getHeight());
        
        // Draw game objects
        for (GameObject obj : gameObjects) {
            obj.render(gc);
        }
        
        // Draw cursor on overlay
        cursorAnimation.render(overlayGc);
        
        // Draw combo counter (example)
        drawComboCounter();
    }
    
    private void drawComboCounter() {
        // Calculate current combo based on hit objects
        int combo = 0;
        for (GameObject obj : gameObjects) {
            if (obj.isHit(currentTime)) {
                combo++;
            } else if (obj.isVisible(currentTime)) {
                break; // Stop counting at first unhit object
            }
        }
        
        if (combo > 0) {
            overlayGc.setFill(Color.WHITE);
            overlayGc.setFont(javafx.scene.text.Font.font("Arial", 48));
            overlayGc.fillText(combo + "x", 20, gameplayCanvas.getHeight() - 20);
        }
    }
    
    public void reset() {
        currentTime = 0;
        isComplete = false;
        setupAnimation();
    }
    
    public void setPlaybackSpeed(double speed) {
        this.playbackSpeed = speed;
    }
    
    public boolean isComplete() {
        return isComplete && currentTime >= ANIMATION_DURATION;
    }
    
    // Inner classes for game objects
    
    private abstract class GameObject {
        protected double x, y;
        protected double appearTime;
        
        public GameObject(double x, double y, double appearTime) {
            this.x = x;
            this.y = y;
            this.appearTime = appearTime;
        }
        
        public abstract void update(double currentTime);
        public abstract void render(GraphicsContext gc);
        public abstract boolean isVisible(double currentTime);
        public abstract boolean isHit(double currentTime);
    }
    
    private class HitCircleObject extends GameObject {
        private double hitTime;
        private boolean hit = false;
        private double hitAnimationTime = 0;
        
        public HitCircleObject(double x, double y, double hitTime) {
            super(x, y, hitTime - APPROACH_TIME);
            this.hitTime = hitTime;
        }
        
        @Override
        public void update(double currentTime) {
            if (!hit && Math.abs(currentTime - hitTime) < HIT_WINDOW) {
                hit = true;
                hitAnimationTime = currentTime;
            }
        }
        
        @Override
        public void render(GraphicsContext gc) {
            if (!isVisible(currentTime)) return;
            
            double timeSinceAppear = currentTime - appearTime;
            
            if (!hit) {
                // Draw hit circle
                if (hitCircle != null) {
                    drawCenteredImage(gc, hitCircle, x, y, 1.0);
                }
                
                // Draw approach circle
                if (approachCircle != null && timeSinceAppear < APPROACH_TIME) {
                    double scale = 2.0 - (timeSinceAppear / APPROACH_TIME);
                    drawCenteredImage(gc, approachCircle, x, y, scale);
                }
                
                // Draw overlay
                if (hitCircleOverlay != null) {
                    drawCenteredImage(gc, hitCircleOverlay, x, y, 1.0);
                }
                
                // Draw number
                int number = gameObjects.indexOf(this) + 1;
                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font("Arial", 24));
                gc.fillText(String.valueOf(number), x - 8, y + 8);
            } else {
                // Draw hit burst animation
                double animTime = currentTime - hitAnimationTime;
                if (animTime < 0.3) {
                    double scale = 1.0 + animTime * 2;
                    double opacity = 1.0 - (animTime / 0.3);
                    
                    gc.save();
                    gc.setGlobalAlpha(opacity);
                    
                    // Draw hit burst effect
                    gc.setFill(Color.rgb(100, 200, 255, opacity));
                    gc.fillOval(x - 30 * scale, y - 30 * scale, 60 * scale, 60 * scale);
                    
                    gc.restore();
                }
            }
        }
        
        @Override
        public boolean isVisible(double currentTime) {
            if (hit) {
                return currentTime - hitAnimationTime < 0.3;
            }
            return currentTime >= appearTime && currentTime <= hitTime + 0.1;
        }
        
        @Override
        public boolean isHit(double currentTime) {
            return hit && currentTime >= hitTime;
        }
    }
    
    private class SliderObject extends GameObject {
        private double endX, endY;
        private double startTime;
        private double duration;
        private boolean started = false;
        private boolean completed = false;
        
        public SliderObject(double startX, double startY, double endX, double endY, 
                          double startTime, double duration) {
            super(startX, startY, startTime - APPROACH_TIME);
            this.endX = endX;
            this.endY = endY;
            this.startTime = startTime;
            this.duration = duration;
        }
        
        @Override
        public void update(double currentTime) {
            if (!started && currentTime >= startTime) {
                started = true;
            }
            if (!completed && currentTime >= startTime + duration) {
                completed = true;
            }
        }
        
        @Override
        public void render(GraphicsContext gc) {
            if (!isVisible(currentTime)) return;
            
            // Draw slider body
            gc.setStroke(Color.rgb(255, 200, 100));
            gc.setLineWidth(60);
            gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            gc.strokeLine(x, y, endX, endY);
            
            // Draw slider border
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(65);
            gc.strokeLine(x, y, endX, endY);
            
            // Draw start circle
            if (hitCircle != null && !started) {
                drawCenteredImage(gc, hitCircle, x, y, 1.0);
                
                // Draw approach circle
                double timeSinceAppear = currentTime - appearTime;
                if (approachCircle != null && timeSinceAppear < APPROACH_TIME) {
                    double scale = 2.0 - (timeSinceAppear / APPROACH_TIME);
                    drawCenteredImage(gc, approachCircle, x, y, scale);
                }
            }
            
            // Draw slider ball
            if (started && !completed) {
                double progress = (currentTime - startTime) / duration;
                double ballX = x + (endX - x) * progress;
                double ballY = y + (endY - y) * progress;
                
                if (sliderFollowCircle != null) {
                    drawCenteredImage(gc, sliderFollowCircle, ballX, ballY, 1.2);
                }
                
                // Draw slider ball
                gc.setFill(Color.WHITE);
                gc.fillOval(ballX - 20, ballY - 20, 40, 40);
            }
            
            // Draw reverse arrow if needed
            if (reverseArrow != null && !started) {
                drawCenteredImage(gc, reverseArrow, endX, endY, 1.0);
            }
        }
        
        @Override
        public boolean isVisible(double currentTime) {
            return currentTime >= appearTime && currentTime <= startTime + duration + 0.1;
        }
        
        @Override
        public boolean isHit(double currentTime) {
            return completed && currentTime >= startTime + duration;
        }
    }
    
    private void drawCenteredImage(GraphicsContext gc, Image image, double x, double y, double scale) {
        if (image == null) return;
        
        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;
        gc.drawImage(image, x - width / 2, y - height / 2, width, height);
    }
    
    // Cursor animation class
    private static class CursorAnimation {
        private final Image cursor;
        private final Image cursorTrail;
        private List<PathPoint> path = new ArrayList<>();
        private List<TrailPoint> trail = new ArrayList<>();
        private double currentX, currentY;
        private static final int MAX_TRAIL_POINTS = 20;
        
        public CursorAnimation(Image cursor, Image cursorTrail) {
            this.cursor = cursor;
            this.cursorTrail = cursorTrail;
        }
        
        public void setPath(List<PathPoint> path) {
            this.path = path;
        }
        
        public void update(double currentTime) {
            // Find current position based on time
            PathPoint prev = null;
            PathPoint next = null;
            
            for (int i = 0; i < path.size() - 1; i++) {
                if (currentTime >= path.get(i).time && currentTime <= path.get(i + 1).time) {
                    prev = path.get(i);
                    next = path.get(i + 1);
                    break;
                }
            }
            
            if (prev != null && next != null) {
                // Interpolate position
                double t = (currentTime - prev.time) / (next.time - prev.time);
                currentX = prev.x + (next.x - prev.x) * t;
                currentY = prev.y + (next.y - prev.y) * t;
            } else if (!path.isEmpty()) {
                // Use last known position
                PathPoint last = path.get(path.size() - 1);
                if (currentTime >= last.time) {
                    currentX = last.x;
                    currentY = last.y;
                } else {
                    PathPoint first = path.get(0);
                    currentX = first.x;
                    currentY = first.y;
                }
            }
            
            // Update trail
            trail.add(new TrailPoint(currentX, currentY, currentTime));
            while (trail.size() > MAX_TRAIL_POINTS) {
                trail.remove(0);
            }
        }
        
        public void render(GraphicsContext gc) {
            // Draw trail
            if (cursorTrail != null) {
                for (int i = 0; i < trail.size(); i++) {
                    TrailPoint tp = trail.get(i);
                    double opacity = (double) i / trail.size() * 0.5;
                    
                    gc.save();
                    gc.setGlobalAlpha(opacity);
                    gc.drawImage(cursorTrail, tp.x - cursorTrail.getWidth() / 2, 
                               tp.y - cursorTrail.getHeight() / 2);
                    gc.restore();
                }
            }
            
            // Draw cursor
            if (cursor != null) {
                gc.drawImage(cursor, currentX - cursor.getWidth() / 2, 
                           currentY - cursor.getHeight() / 2);
            } else {
                // Fallback cursor
                gc.setFill(Color.WHITE);
                gc.fillOval(currentX - 10, currentY - 10, 20, 20);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(2);
                gc.strokeOval(currentX - 10, currentY - 10, 20, 20);
            }
        }
        
        static class PathPoint {
            double x, y, time;
            
            PathPoint(double x, double y, double time) {
                this.x = x;
                this.y = y;
                this.time = time;
            }
        }
        
        static class TrailPoint {
            double x, y, time;
            
            TrailPoint(double x, double y, double time) {
                this.x = x;
                this.y = y;
                this.time = time;
            }
        }
    }
}