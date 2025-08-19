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
    private com.osuskin.tool.service.SkinElementLoader elementLoader;
    
    // UI Images
    private Image scorebarBg;
    private Image scorebarColour;
    private Image[] scorebarColourFrames = new Image[3]; // scorebar-colour-0/1/2.png
    private Image scorebarMarker;
    private Image scorebarKi;
    private Image scorebarKiGlow;
    private Image scorebarKiDanger;
    private Image scorebarKiDanger2;
    private Image[] scoreNumbers = new Image[10];
    private Image[] comboNumbers = new Image[10];
    private Image comboX;
    
    // Additional UI images
    private Image scorebar; // Legacy fallback
    private Image scorePercent; // Percentage symbol for accuracy
    
    // Game state
    private double health = 0.4;  // 0.0 to 1.0 - start at 40% health
    private double previousHealth = 0.4; // For tracking HP changes
    private double healthChangeTime = 0; // Time of last health change
    private boolean healthIncreased = false; // Track if health went up or down
    private int score = 0;
    private int combo = 0;
    private double accuracy = 100.0;
    private int totalHits = 0;
    private int perfect300 = 0;
    private int good100 = 0;
    private int meh50 = 0;
    
    // UI base constants (sized for larger preview)
    private static final double BASE_HEALTH_BAR_WIDTH = 200;
    private static final double BASE_HEALTH_BAR_HEIGHT = 40;  // Taller to fit actual image
    private static final double BASE_HEALTH_BAR_X = 5;
    private static final double BASE_HEALTH_BAR_Y = 20;
    
    // Dynamic scaling
    private double scale = 1.0;
    private double healthBarWidth = BASE_HEALTH_BAR_WIDTH;
    private double healthBarHeight = BASE_HEALTH_BAR_HEIGHT;
    private double healthBarX = BASE_HEALTH_BAR_X;
    private double healthBarY = BASE_HEALTH_BAR_Y;
    
    // Animation timing
    private double currentTime = 0;
    
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
        this.elementLoader = loader;
        // Load health bar elements
        scorebarBg = loader.loadImage("scorebar-bg");
        scorebarColour = loader.loadImage("scorebar-colour");
        
        // Try loading multiple color frames for animated gradient
        for (int i = 0; i < 3; i++) {
            scorebarColourFrames[i] = loader.loadImage("scorebar-colour-" + i);
        }
        
        scorebarMarker = loader.loadImage("scorebar-marker");
        scorebarKi = loader.loadImage("scorebar-ki");
        scorebarKiGlow = loader.loadImage("scorebar-ki-glow");
        scorebarKiDanger = loader.loadImage("scorebar-kidanger");
        scorebarKiDanger2 = loader.loadImage("scorebar-kidanger2");
        
        // Legacy fallback
        scorebar = loader.loadImage("scorebar");
        
        // Load score numbers with custom prefix support
        String scorePrefix = "score";
        String comboPrefix = "score";  // Default combo uses score prefix
        
        if (loader.getCurrentSkin() != null) {
            if (loader.getCurrentSkin().getScorePrefix() != null) {
                scorePrefix = loader.getCurrentSkin().getScorePrefix();
            }
            if (loader.getCurrentSkin().getComboPrefix() != null) {
                comboPrefix = loader.getCurrentSkin().getComboPrefix();
            }
        }
        
        for (int i = 0; i < 10; i++) {
            scoreNumbers[i] = loader.loadImageWithPrefix(scorePrefix, "-" + i);
            if (scoreNumbers[i] == null) {
                scoreNumbers[i] = loader.loadImage("score-" + i);
            }
            
            comboNumbers[i] = loader.loadImageWithPrefix(comboPrefix, "-" + i);
            if (comboNumbers[i] == null) {
                comboNumbers[i] = loader.loadImage("combo-" + i);
            }
        }
        
        comboX = loader.loadImage("combo-x");
        scorePercent = loader.loadImage("score-percent");
    }
    
    /**
     * Update UI state based on hit result.
     */
    public void onHit(HitObject.HitResult result) {
        previousHealth = health;
        
        switch (result) {
            case HIT_300:
                health = Math.min(1.0, health + 0.05);
                combo++;
                score += 300 * Math.max(1, combo / 10);
                perfect300++;
                totalHits++;
                healthIncreased = true;
                break;
            case HIT_100:
                health = Math.min(1.0, health + 0.02);
                combo++;
                score += 100 * Math.max(1, combo / 10);
                good100++;
                totalHits++;
                healthIncreased = true;
                break;
            case HIT_50:
                health = Math.min(1.0, health + 0.01);
                combo++;
                score += 50 * Math.max(1, combo / 10);
                meh50++;
                totalHits++;
                healthIncreased = true;
                break;
            case MISS:
                health = Math.max(0.0, health - 0.10);
                combo = 0;
                totalHits++;
                healthIncreased = false;
                break;
        }
        
        // Track health change time for flash effect
        if (health != previousHealth) {
            healthChangeTime = currentTime;
        }
        
        // Update accuracy
        if (totalHits > 0) {
            double weightedScore = (perfect300 * 300 + good100 * 100 + meh50 * 50);
            double maxScore = totalHits * 300;
            accuracy = (weightedScore / maxScore) * 100.0;
        }
    }
    
    /**
     * Render all UI elements.
     */
    public void render(double canvasWidth, double canvasHeight) {
        // First render the full canvas background (scorebar-bg)
        renderFullBackground(canvasWidth, canvasHeight);
        // Then render health bar fill on top
        renderHealthBarFill();
        // Then render other UI elements on top
        renderScore(canvasWidth);
        renderAccuracy(canvasWidth);
        renderCombo(canvasHeight);
    }
    
    /**
     * Update animation time.
     */
    public void update(double deltaTime) {
        currentTime += deltaTime;
    }
    
    private void renderFullBackground(double canvasWidth, double canvasHeight) {
        // Draw scorebar-bg as full canvas background overlay
        if (scorebarBg != null) {
            // scorebar-bg should be 1366x768 and fill the entire canvas
            // Draw at exact canvas size (should match the image dimensions)
            gc.drawImage(scorebarBg, 0, 0);
        } else if (scorebar != null) {
            // Legacy fallback - draw at original size
            gc.drawImage(scorebar, 0, 0);
        } else {
            // No background image available - draw subtle gradient
            gc.setFill(Color.rgb(20, 20, 25, 0.3));
            gc.fillRect(0, 0, canvasWidth, canvasHeight);
        }
    }
    
    private void renderHealthBarFill() {
        double x = 0;  // Top-left corner
        double y = 0;  // Top-left corner
        
        // Calculate fill dimensions based on scorebar-colour actual dimensions
        double fillWidth = 0;
        double actualBarHeight = 0;
        double actualBarWidth = 0;
        
        if (scorebarColour != null) {
            actualBarWidth = scorebarColour.getWidth();
            actualBarHeight = scorebarColour.getHeight();
            fillWidth = actualBarWidth * health;
        } else if (scorebarColourFrames[0] != null) {
            actualBarWidth = scorebarColourFrames[0].getWidth();
            actualBarHeight = scorebarColourFrames[0].getHeight();
            fillWidth = actualBarWidth * health;
        } else {
            // Fallback dimensions
            actualBarWidth = healthBarWidth;
            actualBarHeight = healthBarHeight;
            fillWidth = actualBarWidth * health;
        }
        
        // Draw health fill with proper gradient/texture
        if (fillWidth > 0) {
            gc.save();
            
            // Clip to health amount with proper height  
            gc.beginPath();
            gc.rect(x, y, fillWidth, actualBarHeight);
            gc.clip();
            
            // Try animated color frames first
            boolean drewColorBar = false;
            if (scorebarColourFrames[0] != null) {
                // Use animated frames based on health level
                int frameIndex = health > 0.5 ? 0 : (health > 0.25 ? 1 : 2);
                Image colorFrame = scorebarColourFrames[frameIndex];
                if (colorFrame != null) {
                    // Draw at original size, no scaling
                    gc.drawImage(colorFrame, x, y);
                    drewColorBar = true;
                }
            }
            
            // Fall back to single color image
            if (!drewColorBar && scorebarColour != null) {
                // Draw at original size, no scaling
                gc.drawImage(scorebarColour, x, y);
                drewColorBar = true;
            }
            
            // Ultimate fallback: gradient fill
            if (!drewColorBar) {
                // Create health-based gradient
                Color startColor, endColor;
                if (health > 0.5) {
                    startColor = Color.rgb(50, 255, 50);
                    endColor = Color.rgb(30, 200, 30);
                } else if (health > 0.25) {
                    startColor = Color.rgb(255, 255, 50);
                    endColor = Color.rgb(200, 200, 30);
                } else {
                    startColor = Color.rgb(255, 50, 50);
                    endColor = Color.rgb(200, 30, 30);
                }
                
                // Simple gradient effect
                gc.setFill(startColor);
                gc.fillRect(x, y + 1, fillWidth, healthBarHeight - 2);
                gc.setFill(Color.rgb(255, 255, 255, 0.2));
                gc.fillRect(x, y + 1, fillWidth, healthBarHeight / 3);
            }
            
            gc.restore();
        }
        
        // Remove the flash box - only show glow on the bar fill itself
        double flashTime = currentTime - healthChangeTime;
        if (flashTime < 0.3 && fillWidth > 0) {
            double flashOpacity = 1.0 - (flashTime / 0.3);
            gc.save();
            
            // Only flash the filled portion of the bar
            gc.beginPath();
            gc.rect(x, y, fillWidth, actualBarHeight);
            gc.clip();
            
            if (healthIncreased) {
                gc.setFill(Color.rgb(100, 255, 100, flashOpacity * 0.2));
            } else {
                gc.setFill(Color.rgb(255, 100, 100, flashOpacity * 0.2));
            }
            gc.fillRect(x, y, fillWidth, actualBarHeight);
            
            gc.restore();
        }
        
        // Draw health marker at current position
        if (scorebarMarker != null && health > 0) {
            // Position based on actual scorebar-colour width
            double markerX = x + fillWidth - scorebarMarker.getWidth() / 2;
            double markerY = y + (actualBarHeight / 2) - scorebarMarker.getHeight() / 2;
            
            // Add subtle bob animation to marker
            double bobOffset = Math.sin(currentTime * 3) * 1.5;
            gc.drawImage(scorebarMarker, markerX, markerY + bobOffset);
        }
    }
    
    private void renderScoreBorder(double scoreX, double scoreY) {
        // These elements are actually the border/decoration around score
        // scorebar-ki elements are score area decorations, not health indicators
        
        // Draw score border/frame elements
        if (scorebarKi != null) {
            // This is the score area border/decoration
            gc.drawImage(scorebarKi, scoreX - 10, scoreY - 5);
        }
        
        if (scorebarKiDanger != null && health < 0.3) {
            // Show danger decoration near score when health is low
            double pulseAlpha = 0.5 + Math.sin(currentTime * 4) * 0.3;
            gc.save();
            gc.setGlobalAlpha(pulseAlpha);
            gc.drawImage(scorebarKiDanger, scoreX - 10, scoreY + 40);
            gc.restore();
        }
        
        if (scorebarKiDanger2 != null && health < 0.1) {
            // Critical health indicator
            double pulseAlpha = 0.7 + Math.sin(currentTime * 8) * 0.3;
            gc.save();
            gc.setGlobalAlpha(pulseAlpha);
            gc.drawImage(scorebarKiDanger2, scoreX - 10, scoreY + 80);
            gc.restore();
            
            // Red screen tint for critical health
            gc.save();
            gc.setGlobalAlpha(0.15 + Math.sin(currentTime * 10) * 0.05);
            gc.setFill(Color.rgb(255, 0, 0));
            gc.fillRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
            gc.restore();
        }
    }
    
    private void renderAccuracy(double canvasWidth) {
        // Position accuracy directly below score, right-aligned
        String accText = String.format("%.2f", accuracy);
        
        // Get actual dimensions from score numbers if available
        double digitWidth = 12;  // Default
        if (scoreNumbers[0] != null) {
            digitWidth = scoreNumbers[0].getWidth() * 0.8;  // Slightly smaller for accuracy
        }
        
        double totalWidth = (accText.length() + 1) * digitWidth * 0.8;  // +1 for %
        double x = canvasWidth - totalWidth - 10;  // Same right alignment as score
        double y = 35;  // Directly below score
        
        // Draw accuracy using score numbers if available (reuse for consistency)
        if (scoreNumbers[0] != null) {
            for (int i = 0; i < accText.length(); i++) {
                char c = accText.charAt(i);
                if (c >= '0' && c <= '9') {
                    int digit = Character.getNumericValue(c);
                    // Draw at original size
                    gc.drawImage(scoreNumbers[digit], x + i * digitWidth * 0.8, y);
                } else if (c == '.') {
                    // Draw decimal point
                    gc.setFill(Color.WHITE);
                    gc.fillOval(x + i * digitWidth + digitWidth/3, y + digitWidth, 3, 3);
                }
            }
            
            // Draw percent symbol at original size
            if (scorePercent != null) {
                gc.drawImage(scorePercent, x + accText.length() * digitWidth * 0.8, y);
            } else {
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Arial", 12));  // No scaling
                gc.fillText("%", x + accText.length() * digitWidth * 0.8 + 2, y + digitWidth);
            }
        } else {
            // Fallback text rendering
            gc.setFill(Color.rgb(200, 200, 200));
            gc.setFont(Font.font("Arial", 14));  // No scaling
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(accText + "%", canvasWidth - 10, y + 12);
        }
    }
    
    private void renderScore(double canvasWidth) {
        String scoreText = String.format("%08d", score);
        // Position score in top-right corner at native resolution
        
        // Get actual dimensions from first score number image if available
        double digitWidth = 15;  // Default
        if (scoreNumbers[0] != null) {
            digitWidth = scoreNumbers[0].getWidth();
        }
        
        // Get score overlap from skin
        double overlap = 0;
        if (elementLoader != null && elementLoader.getCurrentSkin() != null) {
            Integer scoreOverlap = elementLoader.getCurrentSkin().getScoreOverlap();
            if (scoreOverlap != null) {
                overlap = scoreOverlap;  // No scaling
            }
        }
        
        // Calculate total width with overlap
        double spacing = digitWidth - overlap;
        double totalWidth = scoreText.length() * spacing;
        double x = canvasWidth - totalWidth - 10;  // 10px from right edge
        double y = 10;  // Very close to top
        
        // Draw score border/decoration first
        renderScoreBorder(x - 20, y);
        
        if (scoreNumbers[0] != null) {
            // Draw score using score number images at original size
            for (int i = 0; i < scoreText.length(); i++) {
                int digit = Character.getNumericValue(scoreText.charAt(i));
                if (digit >= 0 && digit <= 9 && scoreNumbers[digit] != null) {
                    // Draw at original size
                    gc.drawImage(scoreNumbers[digit], x + i * spacing, y);
                }
            }
        } else {
            // Fallback: draw text
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", 16));  // No scaling
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(scoreText, canvasWidth - 10, y + 15);
        }
    }
    
    private void renderCombo(double canvasHeight) {
        if (combo == 0) return;
        
        // Position combo in bottom-left corner
        double x = 10 * scale;  // Small margin from left
        double y = canvasHeight - (80 * scale);  // Near bottom
        
        String comboText = String.valueOf(combo);
        
        if (comboNumbers[0] != null) {
            // Draw combo using combo number images
            double digitWidth = 40 * scale;  // Bigger digits for combo
            double comboScale = 1.0;
            
            // Get combo overlap from skin
            double overlap = 0;
            if (elementLoader != null && elementLoader.getCurrentSkin() != null) {
                Integer comboOverlap = elementLoader.getCurrentSkin().getComboOverlap();
                if (comboOverlap != null) {
                    overlap = comboOverlap * scale * comboScale;
                }
            }
            
            // Scale up for milestone combos
            if (combo >= 100) comboScale = 1.3;
            else if (combo >= 50) comboScale = 1.15;
            
            double spacing = (digitWidth - overlap) * comboScale;
            
            for (int i = 0; i < comboText.length(); i++) {
                int digit = Character.getNumericValue(comboText.charAt(i));
                if (digit >= 0 && digit <= 9 && comboNumbers[digit] != null) {
                    // Draw at original size, no custom scaling
                    gc.drawImage(comboNumbers[digit], x + i * spacing, y);
                }
            }
            
            // Draw "x" after the number at original size
            if (comboX != null) {
                double xPos = x + comboText.length() * digitWidth * comboScale;
                gc.drawImage(comboX, xPos, y + 10);  // Original size, no scaling
            } else {
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Arial", 20 * comboScale));  // No base scaling
                gc.fillText("x", x + comboText.length() * digitWidth * comboScale + 5, y + 30);
            }
        } else {
            // Fallback: draw text
            gc.setFill(Color.WHITE);
            double fontSize = combo >= 50 ? 45 : 40;  // Bigger font, no scaling
            gc.setFont(Font.font("Arial Bold", fontSize));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(combo + "x", x, y + 50);
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
        health = 0.4;  // Start at 40% health
        score = 0;
        combo = 0;
        accuracy = 100.0;
    }
}