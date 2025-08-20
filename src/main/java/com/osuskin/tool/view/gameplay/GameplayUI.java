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
    private Image[] scorebarColourFrames = null; // Dynamic array for animated scorebar
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
    private double health = 0.25;  // 0.0 to 1.0 - start at 25% health
    private double previousHealth = 0.25; // For tracking HP changes
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
        
        // Try loading animated scorebar frames for full osu! experience
        java.util.List<Image> tempFrames = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) { // Check up to 200 frames
            Image frame = loader.loadImage("scorebar-colour-" + i);
            if (frame != null) {
                tempFrames.add(frame);
            } else if (i > 10) { // If we've loaded at least some frames and hit a gap, stop
                break;
            }
        }
        if (!tempFrames.isEmpty()) {
            scorebarColourFrames = tempFrames.toArray(new Image[0]);
        }
        
        // Load markers without fallback - these are optional health indicators
        scorebarMarker = loader.loadImageNoFallback("scorebar-marker");
        scorebarKi = loader.loadImageNoFallback("scorebar-ki");
        scorebarKiGlow = loader.loadImageNoFallback("scorebar-ki-glow");
        scorebarKiDanger = loader.loadImageNoFallback("scorebar-kidanger");
        scorebarKiDanger2 = loader.loadImageNoFallback("scorebar-kidanger2");
        
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
                health = Math.min(1.0, health + 0.08);  // Increased from 0.05
                combo++;
                score += 300 * Math.max(1, combo / 10);
                perfect300++;
                totalHits++;
                healthIncreased = true;
                break;
            case HIT_100:
                health = Math.min(1.0, health + 0.04);  // Increased from 0.02
                combo++;
                score += 100 * Math.max(1, combo / 10);
                good100++;
                totalHits++;
                healthIncreased = true;
                break;
            case HIT_50:
                health = Math.min(1.0, health + 0.02);  // Increased from 0.01
                combo++;
                score += 50 * Math.max(1, combo / 10);
                meh50++;
                totalHits++;
                healthIncreased = true;
                break;
            case MISS:
                health = Math.max(0.0, health - 0.10);  // Keep same penalty
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
        // Correct order: scorebar-bg first, then scorebar-colour on top
        
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
        // IMPORTANT: In osu!, scorebar-bg is drawn AFTER scorebar-colour
        // We're currently drawing it first which might cover the health bar
        // This is kept for now but might need to be reversed
        
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
        // TEST: Always position scorebar-colour as if no markers exist
        // This ignores marker presence and always uses (5, 16)
        double x, y;
        
        // TEMPORARILY DISABLED: Marker-based positioning
        // boolean hasAnyMarker = (scorebarMarker != null || scorebarKi != null || 
        //                         scorebarKiDanger != null || scorebarKiDanger2 != null);
        // if (hasAnyMarker) {
        //     x = 12;
        //     y = 12;
        // } else {
        //     x = 5;
        //     y = 16;
        // }
        
        // ALWAYS use no-marker positioning for testing
        x = 5;
        y = 16;
        
        // Calculate fill dimensions based on scorebar-colour actual dimensions
        double fillWidth = 0;
        double actualBarHeight = 0;
        double actualBarWidth = 0;
        
        if (scorebarColour != null) {
            actualBarWidth = scorebarColour.getWidth();
            actualBarHeight = scorebarColour.getHeight();
            fillWidth = actualBarWidth * health;
        } else if (scorebarColourFrames != null && scorebarColourFrames.length > 0) {
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
            
            // Clip to health amount - only clip width, not height
            // Use a very large height to ensure no vertical clipping occurs
            gc.beginPath();
            gc.rect(x, 0, fillWidth, 10000);  // Effectively no height limit
            gc.clip();
            
            // Try animated color frames first
            boolean drewColorBar = false;
            if (scorebarColourFrames != null && scorebarColourFrames.length > 0) {
                // For animated scorebar, pick frame based on health percentage
                // Map health (0.0 to 1.0) to frame index
                int frameIndex = (int)(health * (scorebarColourFrames.length - 1));
                frameIndex = Math.max(0, Math.min(frameIndex, scorebarColourFrames.length - 1));
                
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
        
        // Flash effect removed - no visual feedback on health changes
        
        // Draw health marker at current position (using same x, y from health bar)
        renderHealthMarker(x, y, fillWidth, actualBarHeight);
    }
    
    private void renderHealthMarker(double barX, double barY, double fillWidth, double barHeight) {
        // Choose the appropriate marker based on health level
        Image markerToUse = null;
        double pulseScale = 1.0;
        
        if (health > 0 && fillWidth > 0) {
            // Priority order: kidanger2 -> kidanger -> ki -> marker
            if (health < 0.1 && scorebarKiDanger2 != null) {
                markerToUse = scorebarKiDanger2;
                pulseScale = 1.0 + Math.sin(currentTime * 10) * 0.1; // Fast pulse when critical
            } else if (health < 0.3 && scorebarKiDanger != null) {
                markerToUse = scorebarKiDanger;
                pulseScale = 1.0 + Math.sin(currentTime * 6) * 0.05; // Medium pulse when low
            } else if (scorebarKi != null) {
                markerToUse = scorebarKi;
            } else if (scorebarMarker != null) {
                markerToUse = scorebarMarker;
            }
            
            if (markerToUse != null) {
                // Marker should be positioned at the end of the health fill
                double markerWidth = markerToUse.getWidth() * pulseScale;
                double markerHeight = markerToUse.getHeight() * pulseScale;
                double markerX = barX + fillWidth - (markerWidth / 2);
                // Vertically center the marker on the health bar
                double markerY = barY + (barHeight / 2) - (markerHeight / 2);
                
                // Add subtle bob animation to marker
                double bobOffset = Math.sin(currentTime * 3) * 1.5;
                
                gc.save();
                if (pulseScale != 1.0) {
                    // Apply scaling for pulse effect
                    gc.translate(markerX + markerWidth/2, markerY + markerHeight/2);
                    gc.scale(pulseScale, pulseScale);
                    gc.drawImage(markerToUse, -markerWidth/2, -markerHeight/2 + bobOffset);
                } else {
                    gc.drawImage(markerToUse, markerX, markerY + bobOffset);
                }
                gc.restore();
            }
        }
    }
    
    private void renderScoreBorder(double scoreX, double scoreY) {
        // scorebar-ki elements are actually health markers, not score decorations
        // They are rendered as part of the health bar marker system
        
        // Add red screen tint for critical health as visual feedback
        if (health < 0.1) {
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
        
        // Scale factor for accuracy display (smaller than score)
        double accScale = 0.48;  // 48% of original size (even smaller)
        
        // Get actual dimensions from score numbers if available
        double digitWidth = 12;  // Default
        if (scoreNumbers[0] != null) {
            digitWidth = scoreNumbers[0].getWidth() * accScale;
        }
        
        // Reduce digit spacing for tighter accuracy display
        digitWidth *= 0.9;  // 10% tighter spacing between digits
        
        // Check if score-percent.png is being used for positioning (centering trick)
        boolean usePercentPositioning = false;
        double percentWidth = 0;
        if (scorePercent != null) {
            percentWidth = scorePercent.getWidth();
            double percentHeight = scorePercent.getHeight();
            // If percent image is unusually wide (>100px or >3x height), it's likely used for positioning
            usePercentPositioning = percentWidth > 100 || percentWidth > percentHeight * 3;
        }
        
        // Position and render based on whether percent positioning is used
        if (usePercentPositioning && scorePercent != null) {
            // The skin uses score-percent.png width to position accuracy
            // In osu!, the entire accuracy assembly is right-aligned to the percent image edge
            double scaledPercentWidth = percentWidth * accScale;
            double percentX = canvasWidth - scaledPercentWidth;
            double y = 48;  // Even lower position
            
            // Calculate where digits should be drawn (working backwards from percent position)
            double dotWidth = digitWidth * 0.25;  // Even smaller decimal point
            double digitsWidth = 0;
            for (char c : accText.toCharArray()) {
                if (c == '.') {
                    digitsWidth += dotWidth;
                } else {
                    digitsWidth += digitWidth;
                }
            }
            
            // Start position for digits (to the left of where percent actually appears in the image)
            // We assume the visible percent is at the right edge of the image
            double currentX = canvasWidth - scaledPercentWidth - digitsWidth;
            
            // Draw the digits
            if (scoreNumbers[0] != null) {
                gc.save();
                for (int i = 0; i < accText.length(); i++) {
                    char c = accText.charAt(i);
                    if (c >= '0' && c <= '9') {
                        int digit = Character.getNumericValue(c);
                        gc.save();
                        gc.scale(accScale, accScale);
                        gc.drawImage(scoreNumbers[digit], currentX / accScale, y / accScale);
                        gc.restore();
                        currentX += digitWidth;
                    } else if (c == '.') {
                        gc.setFill(Color.WHITE);
                        double dotX = currentX + dotWidth * 0.3;
                        double dotY = y + digitWidth * 0.75;
                        gc.fillOval(dotX, dotY, 2.5, 2.5);
                        currentX += dotWidth;
                    }
                }
                gc.restore();
            }
            
            // Draw the percent image at its full width (includes positioning padding)
            gc.save();
            gc.scale(accScale, accScale);
            gc.drawImage(scorePercent, percentX / accScale, y / accScale);
            gc.restore();
            
        } else {
            // Normal positioning - right-aligned to canvas edge
            double dotWidth = digitWidth * 0.25;  // Smaller decimal point for tighter spacing
            double totalWidth = 0;
            
            // Calculate total width
            for (char c : accText.toCharArray()) {
                if (c == '.') {
                    totalWidth += dotWidth;
                } else {
                    totalWidth += digitWidth;
                }
            }
            if (scorePercent != null) {
                totalWidth += scorePercent.getWidth() * accScale;
            } else {
                totalWidth += digitWidth; // Space for % character
            }
            
            double x = canvasWidth - totalWidth - 10;  // More padding for accuracy
            double y = 48;  // Even lower position
            
            // Draw accuracy digits and percent normally
            if (scoreNumbers[0] != null) {
                gc.save();
                double currentX = x;
                
                for (int i = 0; i < accText.length(); i++) {
                    char c = accText.charAt(i);
                    if (c >= '0' && c <= '9') {
                        int digit = Character.getNumericValue(c);
                        gc.save();
                        gc.scale(accScale, accScale);
                        gc.drawImage(scoreNumbers[digit], currentX / accScale, y / accScale);
                        gc.restore();
                        currentX += digitWidth;
                    } else if (c == '.') {
                        gc.setFill(Color.WHITE);
                        double dotX = currentX + dotWidth * 0.3;
                        double dotY = y + digitWidth * 0.75;
                        gc.fillOval(dotX, dotY, 2.5, 2.5);
                        currentX += dotWidth;
                    }
                }
                
                // Draw percent symbol
                if (scorePercent != null) {
                    gc.save();
                    gc.scale(accScale, accScale);
                    gc.drawImage(scorePercent, currentX / accScale, y / accScale);
                    gc.restore();
                } else {
                    gc.setFill(Color.WHITE);
                    gc.setFont(Font.font("Arial", 10));
                    gc.fillText("%", currentX + 2, y + digitWidth * 0.8);
                }
                gc.restore();
            } else {
                // Fallback text rendering
                gc.setFill(Color.rgb(200, 200, 200));
                gc.setFont(Font.font("Arial", 11));
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(accText + "%", canvasWidth - 10, y + 10);
            }
        }
    }
    
    private void renderScore(double canvasWidth) {
        String scoreText = String.format("%08d", score);
        // Position score in top-right corner with scaling
        
        // Scale factor for score display
        double scoreScale = 0.8;  // 80% of original size (significantly bigger)
        
        // Get actual dimensions from first score number image if available
        double digitWidth = 15;  // Default
        if (scoreNumbers[0] != null) {
            digitWidth = scoreNumbers[0].getWidth() * scoreScale;
        }
        
        // Get score overlap from skin
        double overlap = 0;
        if (elementLoader != null && elementLoader.getCurrentSkin() != null) {
            Integer scoreOverlap = elementLoader.getCurrentSkin().getScoreOverlap();
            if (scoreOverlap != null) {
                overlap = scoreOverlap * scoreScale;  // Scale the overlap too
            }
        }
        // Add extra overlap to reduce gaps between digits
        overlap += 3 * scoreScale;  // Tighter spacing
        
        // Calculate total width with overlap
        double spacing = digitWidth - overlap;
        double totalWidth = scoreText.length() * spacing;
        double x = canvasWidth - totalWidth - 6;  // Slightly more padding to the right
        double y = 3;  // Very close to top edge
        
        // Draw score border/decoration first
        renderScoreBorder(x - 20, y);
        
        if (scoreNumbers[0] != null) {
            // Draw score using score number images with scaling
            gc.save();
            for (int i = 0; i < scoreText.length(); i++) {
                int digit = Character.getNumericValue(scoreText.charAt(i));
                if (digit >= 0 && digit <= 9 && scoreNumbers[digit] != null) {
                    // Draw with scaling
                    double digitX = x + i * spacing;
                    gc.save();
                    gc.scale(scoreScale, scoreScale);
                    gc.drawImage(scoreNumbers[digit], digitX / scoreScale, y / scoreScale);
                    gc.restore();
                }
            }
            gc.restore();
        } else {
            // Fallback: draw text
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", 13));  // Smaller font
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(scoreText, canvasWidth - 6, y + 12);
        }
    }
    
    private void renderCombo(double canvasHeight) {
        if (combo == 0) return;
        
        // Position combo in bottom-left corner
        double x = 10 * scale;  // Small margin from left
        double y = canvasHeight - (60 * scale);  // Lower position (closer to bottom)
        
        String comboText = String.valueOf(combo);
        
        if (comboNumbers[0] != null) {
            // Base scale for combo display (smaller overall)
            double baseComboScale = 0.85;  // 85% of original size
            
            // Draw combo using combo number images
            double digitWidth = comboNumbers[0].getWidth() * scale * baseComboScale;
            double comboScale = 1.0;  // Additional scale for milestones
            
            // Get combo overlap from skin
            double overlap = 0;
            if (elementLoader != null && elementLoader.getCurrentSkin() != null) {
                Integer comboOverlap = elementLoader.getCurrentSkin().getComboOverlap();
                if (comboOverlap != null) {
                    overlap = comboOverlap * scale * baseComboScale * comboScale;
                }
            }
            
            // Scale up for milestone combos (but less dramatically)
            if (combo >= 100) comboScale = 1.2;
            else if (combo >= 50) comboScale = 1.1;
            
            double spacing = (digitWidth - overlap) * comboScale;
            double finalScale = baseComboScale * comboScale;
            
            for (int i = 0; i < comboText.length(); i++) {
                int digit = Character.getNumericValue(comboText.charAt(i));
                if (digit >= 0 && digit <= 9 && comboNumbers[digit] != null) {
                    // Draw with combined scaling
                    gc.save();
                    gc.scale(finalScale, finalScale);
                    gc.drawImage(comboNumbers[digit], (x + i * spacing) / finalScale, y / finalScale);
                    gc.restore();
                }
            }
            
            // Draw "x" after the number with same scaling
            if (comboX != null) {
                double xPos = x + comboText.length() * spacing;
                gc.save();
                gc.scale(finalScale, finalScale);
                gc.drawImage(comboX, xPos / finalScale, (y + 5) / finalScale);
                gc.restore();
            } else {
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Arial", 18 * finalScale));
                gc.fillText("x", x + comboText.length() * spacing + 5, y + digitWidth);
            }
        } else {
            // Fallback: draw text
            gc.setFill(Color.WHITE);
            double fontSize = combo >= 50 ? 38 : 35;  // Smaller fonts
            gc.setFont(Font.font("Arial Bold", fontSize));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(combo + "x", x, y + 40);
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
        health = 0.25;  // Start at 25% health
        score = 0;
        combo = 0;
        accuracy = 100.0;
    }
}