# Default Skin Setup Instructions

## Quick Setup Guide

### Step 1: Get the Default osu! Skin Files

The default osu! skin files can be found in your osu! installation:
- **Windows**: `C:\Program Files\osu!\Skins\Default\` or in the osu! folder
- **Or**: Download the default skin template from the osu! website

### Step 2: Copy Required Files

Copy the following **essential files** into this folder for the preview to work properly:

#### Minimum Required (Priority 1)
```
hitcircle.png
hitcircleoverlay.png  
approachcircle.png
default-1.png through default-9.png (combo numbers)
cursor.png
```

#### Hit Feedback (Priority 2)
```
hit0.png (miss)
hit50.png
hit100.png
hit300.png
lighting.png
```

#### Sliders (Priority 3)
```
sliderb.png
sliderball.png
sliderfollowcircle.png
reversearrow.png
```

#### UI Elements (Priority 4)
```
scorebar-bg.png
scorebar-colour.png
combo-0.png through combo-9.png
combo-x.png
```

### Step 3: Verify

After copying files, your folder structure should look like:
```
default-skin/
├── README.md (this file)
├── REQUIRED_ELEMENTS.md (full list)
├── hitcircle.png
├── hitcircleoverlay.png
├── approachcircle.png
├── default-1.png
├── default-2.png
├── ... (etc)
```

## How It Works

When the preview loads a skin element:
1. First, it tries to load from the selected skin
2. If not found, it loads from this default-skin folder
3. This ensures the preview always works, even with incomplete skins

## Testing

To test if the default skin is working:
1. Select a skin that's missing some elements
2. Open the preview
3. Missing elements should display using the default skin files

## Notes

- Files must be PNG format
- Keep original file names (case-sensitive)
- Don't resize images - the loader handles scaling
- You can start with just Priority 1 files and add more later