package com.osuskin.tool.view.gameplay;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Manages the gameplay UI elements (health bar, score, combo counter, etc.)
 */
public class GameplayUI {
    
    private final GraphicsContext gc;
    
    // UI Images
    private Image scorebarBg;
    private Image scorebarColour;
    private Image scorebarMarker;
    private Image scorebarKi;
    private Image scorebarKiDanger;
    private Image scorebarKiDanger2;
    private Image[] scoreNumbers = new Image[10];
    private Image[] comboNumbers = new Image[10];
    private Image comboX;
    
    // Game state
    private double health = 1.0;  // 0.0 to 1.0
    private int score = 0;
    private int combo = 0;
    private double accuracy = 100.0;
    
    // UI base constants (sized for larger preview)
    private static final double BASE_HEALTH_BAR_WIDTH = 250;
    private static final double BASE_HEALTH_BAR_HEIGHT = 15;
    private static final double BASE_HEALTH_BAR_X = 15;
    private static final double BASE_HEALTH_BAR_Y = 15;
    
    // Dynamic scaling
    private double scale = 1.0;
    private double healthBarWidth = BASE_HEALTH_BAR_WIDTH;
    private double healthBarHeight = BASE_HEALTH_BAR_HEIGHT;
    private double healthBarX = BASE_HEALTH_BAR_X;
    private double healthBarY = BASE_HEALTH_BAR_Y;
    
    public GameplayUI(GraphicsContext gc) {
        this.gc = gc;
    }
    
    /**
     * Set the scale factor for UI elements.
     */
    public void setScale(double scale) {
        this.scale = scale;
        healthBarWidth = BASE_HEALTH_BAR_WIDTH * scale;
        healthBarHeight = BASE_HEALTH_BAR_HEIGHT * scale;
        healthBarX = BASE_HEALTH_BAR_X * scale;
        healthBarY = BASE_HEALTH_BAR_Y * scale;
    }
    
    /**
     * Load UI element images.
     */
    public void loadElements(com.osuskin.tool.service.SkinElementLoader loader) {
        // Load health bar elements
        scorebarBg = loader.loadImage("scorebar-bg");
        scorebarColour = loader.loadImage("scorebar-colour");
        scorebarMarker = loader.loadImage("scorebar-marker");
        scorebarKi = loader.loadImage("scorebar-ki");
        scorebarKiDanger = loader.loadImage("scorebar-kidanger");
        scorebarKiDanger2 = loader.loadImage("scorebar-kidanger2");
        
        // Load score numbers
        for (int i = 0; i < 10; i++) {
            scoreNumbers[i] = loader.loadImage("score-" + i);
            comboNumbers[i] = loader.loadImage("combo-" + i);
        }
        
        comboX = loader.loadImage("combo-x");
    }
    
    /**
     * Update UI state based on hit result.
     */
    public void onHit(HitObject.HitResult result) {
        switch (result) {
            case HIT_300:
                health = Math.min(1.0, health + 0.05);
                combo++;
                break;
            case HIT_100:
                health = Math.min(1.0, health + 0.02);
                combo++;
                break;
            case HIT_50:
                health = Math.min(1.0, health + 0.01);
                combo++;
                break;
            case MISS:
                health = Math.max(0.0, health - 0.10);
                combo = 0;
                break;
        }
    }
    
    /**
     * Render all UI elements.
     */
    public void render(double canvasWidth, double canvasHeight) {
        renderHealthBar();
        renderScore(canvasWidth);
        renderCombo(canvasHeight);
        // Accuracy removed - not needed for preview
    }
    
    private void renderHealthBar() {
        double x = healthBarX;
        double y = healthBarY;
        
        // Draw health bar background
        if (scorebarBg != null) {
            // Scale and draw the background image
            double bgScale = healthBarWidth / scorebarBg.getWidth();
            gc.drawImage(scorebarBg, x, y, 
                        scorebarBg.getWidth() * bgScale, 
                        scorebarBg.getHeight() * bgScale);
        } else {
            // Fallback: draw simple background
            gc.setFill(Color.rgb(50, 50, 50, 0.8));
            gc.fillRoundRect(x, y, healthBarWidth, healthBarHeight, 5, 5);
        }
        
        // Draw health fill
        double fillWidth = healthBarWidth * health;
        if (scorebarColour != null && fillWidth > 0) {
            // Draw the colored bar image scaled to health
            gc.save();
            // Use rect clipping instead of setClip
            gc.beginPath();
            gc.rect(x, y, fillWidth, healthBarHeight);
            gc.clip();
            double colorScale = healthBarWidth / scorebarColour.getWidth();
            gc.drawImage(scorebarColour, x, y,
                        scorebarColour.getWidth() * colorScale,
                        scorebarColour.getHeight() * colorScale);
            gc.restore();
        } else if (fillWidth > 0) {
            // Fallback: draw simple colored bar
            Color healthColor;
            if (health > 0.5) {
                healthColor = Color.rgb(100, 200, 100);
            } else if (health > 0.25) {
                healthColor = Color.rgb(200, 200, 100);
            } else {
                healthColor = Color.rgb(200, 100, 100);
            }
            gc.setFill(healthColor);
            gc.fillRoundRect(x + 2, y + 2, fillWidth - 4, healthBarHeight - 4, 3, 3);
        }
        
        // Draw health marker (optional)
        if (scorebarMarker != null && health > 0) {
            double markerX = x + (healthBarWidth * health) - scorebarMarker.getWidth() / 2;
            gc.drawImage(scorebarMarker, markerX, y - 2);
        }
        
        // Draw ki danger indicators based on health level
        if (health < 0.5 && scorebarKi != null) {
            // Show ki indicator when health is below 50%
            gc.drawImage(scorebarKi, x + healthBarWidth + 5, y - 5, 
                        scorebarKi.getWidth() * scale * 0.8, 
                        scorebarKi.getHeight() * scale * 0.8);
        }
        
        if (health < 0.3 && scorebarKiDanger != null) {
            // Show danger indicator when health is critical
            double pulseScale = 1.0 + Math.sin(System.currentTimeMillis() / 200.0) * 0.1;
            gc.drawImage(scorebarKiDanger, x + healthBarWidth + 25, y - 5,
                        scorebarKiDanger.getWidth() * scale * 0.8 * pulseScale,
                        scorebarKiDanger.getHeight() * scale * 0.8 * pulseScale);
        }
        
        if (health < 0.1 && scorebarKiDanger2 != null) {
            // Show extreme danger indicator when near death
            double pulseScale = 1.0 + Math.sin(System.currentTimeMillis() / 100.0) * 0.15;
            gc.setGlobalAlpha(0.8 + Math.sin(System.currentTimeMillis() / 150.0) * 0.2);
            gc.drawImage(scorebarKiDanger2, x + healthBarWidth + 45, y - 5,
                        scorebarKiDanger2.getWidth() * scale * 0.8 * pulseScale,
                        scorebarKiDanger2.getHeight() * scale * 0.8 * pulseScale);
            gc.setGlobalAlpha(1.0);
        }
    }
    
    private void renderScore(double canvasWidth) {
        String scoreText = String.format("%08d", score);
        double x = canvasWidth - (200 * scale);  // Moved left
        double y = 25 * scale;
        
        if (scoreNumbers[0] != null) {
            // Draw score using score number images
            double digitWidth = 20 * scale;
            for (int i = 0; i < scoreText.length(); i++) {
                int digit = Character.getNumericValue(scoreText.charAt(i));
                if (digit >= 0 && digit <= 9 && scoreNumbers[digit] != null) {
                    gc.drawImage(scoreNumbers[digit], x + i * digitWidth, y, digitWidth, digitWidth * 1.2);
                }
            }
        } else {
            // Fallback: draw text
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", 20 * scale));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(scoreText, x + (140 * scale), y + (15 * scale));
        }
    }
    
    private void renderCombo(double canvasHeight) {
        if (combo == 0) return;
        
        double x = 30 * scale;
        double y = canvasHeight - (50 * scale);  // Moved closer to bottom
        
        String comboText = String.valueOf(combo);
        
        if (comboNumbers[0] != null) {
            // Draw combo using combo number images
            double digitWidth = 30 * scale;
            double comboScale = 1.0;
            
            // Scale up for milestone combos
            if (combo >= 100) comboScale = 1.2;
            else if (combo >= 50) comboScale = 1.1;
            
            for (int i = 0; i < comboText.length(); i++) {
                int digit = Character.getNumericValue(comboText.charAt(i));
                if (digit >= 0 && digit <= 9 && comboNumbers[digit] != null) {
                    double width = digitWidth * comboScale;
                    double height = digitWidth * 1.5 * comboScale;
                    gc.drawImage(comboNumbers[digit], x + i * width, y, width, height);
                }
            }
            
            // Draw "x" after the number
            if (comboX != null) {
                double xPos = x + comboText.length() * digitWidth * comboScale;
                gc.drawImage(comboX, xPos, y + (10 * scale), digitWidth * comboScale * 0.7, digitWidth * comboScale);
            } else {
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Arial", 20 * scale * comboScale));
                gc.fillText("x", x + comboText.length() * digitWidth * comboScale + (5 * scale), y + (30 * scale));
            }
        } else {
            // Fallback: draw text
            gc.setFill(Color.WHITE);
            double fontSize = combo >= 50 ? 36 * scale : 30 * scale;
            gc.setFont(Font.font("Arial Bold", fontSize));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(combo + "x", x, y + (40 * scale));
        }
    }
    
    // Accuracy display removed - not needed for skin preview
    
    // Setters for external updates
    public void setHealth(double health) {
        this.health = Math.max(0.0, Math.min(1.0, health));
    }
    
    public void setScore(int score) {
        this.score = score;
    }
    
    public void setCombo(int combo) {
        this.combo = combo;
    }
    
    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }
    
    public void reset() {
        health = 1.0;
        score = 0;
        combo = 0;
        accuracy = 100.0;
    }
}