# Required Default Skin Elements for Preview

This folder should contain the default osu! skin elements used as fallbacks when a custom skin is missing elements.

## Essential Elements for Preview

### Hit Circle Elements
- [ ] `hitcircle.png` - Main circle texture
- [ ] `hitcircleoverlay.png` - Overlay for hit circles
- [ ] `approachcircle.png` - Approach circle indicator

### Hit Burst Animations
- [ ] `hit0.png` - Miss indicator
- [ ] `hit50.png` - 50 score burst
- [ ] `hit100.png` - 100 score burst
- [ ] `hit300.png` - 300 score burst
- [ ] `lighting.png` - Flash effect on hit

### Combo Numbers (required)
- [ ] `default-0.png` - Number 0
- [ ] `default-1.png` - Number 1
- [ ] `default-2.png` - Number 2
- [ ] `default-3.png` - Number 3
- [ ] `default-4.png` - Number 4
- [ ] `default-5.png` - Number 5
- [ ] `default-6.png` - Number 6
- [ ] `default-7.png` - Number 7
- [ ] `default-8.png` - Number 8
- [ ] `default-9.png` - Number 9

### Slider Elements
- [ ] `sliderb.png` - Slider body/track
- [ ] `sliderfollowcircle.png` - Circle around slider ball
- [ ] `sliderball.png` - Ball that moves along slider
- [ ] `reversearrow.png` - Arrow for repeating sliders
- [ ] `sliderscorepoint.png` - Slider tick

### Cursor Elements
- [ ] `cursor.png` - Main cursor image
- [ ] `cursortrail.png` - Cursor trail effect

### Health Bar Elements
- [ ] `scorebar-bg.png` - Health bar background
- [ ] `scorebar-colour.png` - Health bar fill

### Combo Counter Numbers
- [ ] `combo-0.png` - Combo number 0
- [ ] `combo-1.png` - Combo number 1
- [ ] `combo-2.png` - Combo number 2
- [ ] `combo-3.png` - Combo number 3
- [ ] `combo-4.png` - Combo number 4
- [ ] `combo-5.png` - Combo number 5
- [ ] `combo-6.png` - Combo number 6
- [ ] `combo-7.png` - Combo number 7
- [ ] `combo-8.png` - Combo number 8
- [ ] `combo-9.png` - Combo number 9
- [ ] `combo-x.png` - Combo X symbol

### Score Numbers
- [ ] `score-0.png` - Score number 0
- [ ] `score-1.png` - Score number 1
- [ ] `score-2.png` - Score number 2
- [ ] `score-3.png` - Score number 3
- [ ] `score-4.png` - Score number 4
- [ ] `score-5.png` - Score number 5
- [ ] `score-6.png` - Score number 6
- [ ] `score-7.png` - Score number 7
- [ ] `score-8.png` - Score number 8
- [ ] `score-9.png` - Score number 9
- [ ] `score-comma.png` - Score comma separator
- [ ] `score-percent.png` - Percent symbol

## Optional But Recommended

### Animated Hit Bursts (if available)
- [ ] `hit0-0.png` - Miss animation frame 0
- [ ] `hit50-0.png` - 50 animation frame 0
- [ ] `hit100-0.png` - 100 animation frame 0
- [ ] `hit300-0.png` - 300 animation frame 0
- [ ] `hit100k-0.png` - 100 katu frame 0

### Particle Effects
- [ ] `particle50.png` - Particle for 50 hits
- [ ] `particle100.png` - Particle for 100 hits  
- [ ] `particle300.png` - Particle for 300 hits

### Additional UI Elements
- [ ] `scorebar-ki.png` - Health bar danger indicator
- [ ] `scorebar-kidanger.png` - Health bar critical indicator
- [ ] `scorebar-kidanger2.png` - Health bar critical alt

## Instructions

1. Copy all these files from the default osu! skin into this folder
2. Make sure files are named exactly as listed (case-sensitive)
3. PNG format is required for all images
4. Keep original dimensions - the loader will handle scaling

## File Structure
```
default-skin/
├── REQUIRED_ELEMENTS.md (this file)
├── hitcircle.png
├── hitcircleoverlay.png
├── approachcircle.png
├── hit0.png
├── hit50.png
├── hit100.png
├── hit300.png
├── lighting.png
├── default-0.png
├── default-1.png
├── ... (etc)
```

## Notes
- These files serve as fallbacks when custom skins are missing elements
- The preview will work without all files, but having them ensures consistency
- Priority files are marked as "Essential" - add these first