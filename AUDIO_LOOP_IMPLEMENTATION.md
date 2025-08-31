# Audio System Continuous Playback Implementation

## Overview
The audio system has been modified to support continuous looping playback that persists through skin changes. When switching skins while an arrangement is playing, the system will:
1. Keep track of the currently playing arrangement
2. Reload the hitsounds with the new skin's sounds
3. Restart playback from position 0 while maintaining the loop

## Key Changes

### ArrangementPlayer.java
- Added overloaded `playArrangement(String arrangementName, boolean reloadHitsoundsOnly)` method
- Added `reloadHitsoundsAndRestart()` method to handle hitsound reloading
- Added `getCurrentArrangementName()` getter to track current arrangement
- When `reloadHitsoundsOnly` is true, the method skips stopping the player and just reloads hitsounds

### MainController.java
- Added `pendingArrangementReload` field to track arrangements that need reloading
- Modified `resetAudioSystemForNewSkin()` to check if an arrangement is playing
- If playing, stores the arrangement name for later reload instead of stopping
- After skin loads and element loader is updated, checks for pending reload
- Automatically reloads the arrangement with new hitsounds if one was playing

## Behavior

### When Switching Skins While Playing:
1. The arrangement continues playing (no interruption)
2. Hitsound cache is cleared
3. New skin elements are loaded
4. Once loaded, the arrangement is reloaded with new hitsounds
5. Playback restarts from position 0 with the new skin's sounds
6. Looping continues as configured

### When Switching Arrangements While Playing:
1. If currently playing, the new arrangement starts automatically
2. Play button stays in the "pause" state (⏸)
3. Status shows "Loading" then "Playing" for the new arrangement
4. No need to click play again when changing arrangements

### When Switching Skins While Not Playing:
- Normal behavior: stops all audio and resets UI as before

## Technical Details

The implementation uses a two-phase approach:
1. **Detection Phase**: When skin changes, check if arrangement is playing
2. **Reload Phase**: After new skin loads, reload arrangement with new hitsounds

This ensures smooth transition and proper synchronization between background music and hitsounds.

## Testing
To test the implementation:
1. Select a skin
2. Play an arrangement from the dropdown
3. While playing, switch to a different skin
4. The music should continue and restart with the new skin's hitsounds