# osu! Skin Element Rendering Documentation

## Hit Circle Rendering Order (Bottom to Top)

When a hit circle appears in osu!, the following elements are rendered in this specific order:

### 1. **hitcircle.png**
- The main circle texture
- This is the base colored circle that appears
- Usually contains the circle border and fill

### 2. **hitcircleoverlay.png** 
- Overlaid on top of hitcircle.png
- Often contains additional effects, gradients, or decorative elements
- Can be animated (hitcircleoverlay-0.png, hitcircleoverlay-1.png, etc.)
- This layer is typically used for the white/light overlay effect

### 3. **default-[0-9].png** (Combo Numbers)
- The number displayed in the center of the circle
- Shows the order in which circles should be hit (1, 2, 3, etc.)
- Uses default-1.png through default-9.png, default-0.png for 10+

### 4. **approachcircle.png**
- The outer circle that shrinks toward the hit circle
- Indicates timing - when it matches the hit circle size, that's when to click
- Starts at about 2x the size of the hit circle and shrinks down

## Slider Rendering Order

### 1. **sliderb.png** (Slider Body/Track)
- The main slider path/track
- Can be animated (sliderb-0.png, sliderb-1.png, etc.)
- Repeats along the slider path

### 2. **sliderscorepoint.png**
- Slider ticks along the path
- Shows where bonus points can be earned

### 3. **sliderstartcircle.png** (or uses hitcircle)
- The starting point of the slider
- Often just uses the regular hitcircle elements

### 4. **sliderball.png**
- The ball that moves along the slider path
- Player must keep cursor on this ball

### 5. **sliderfollowcircle.png**
- Larger circle around the slider ball
- Shows the area where the cursor needs to stay

### 6. **reversearrow.png**
- Arrow at the end of repeating sliders
- Indicates the slider will reverse direction

## Hit Burst Animation (After Hit)

When a circle is successfully hit, these appear in order:

### 1. **lighting.png**
- Brief flash/glow effect at hit location
- Appears immediately on hit

### 2. **hit[score].png** (hit300, hit100, hit50, hit0)
- Shows the score achieved for that hit
- Can be animated (hit300-0.png, hit300-1.png, etc.)
- Typically fades out while moving slightly upward

### 3. **particle[score].png** (particle300, particle100, particle50)
- Additional particle effects for hits
- Usually star or spark effects that fly outward

## Cursor Elements

### 1. **cursortrail.png**
- Trail that follows behind the cursor
- Multiple instances create the trailing effect
- Each instance fades out over time

### 2. **cursor.png** (or cursormiddle.png)
- The main cursor image
- Always rendered on top of everything else
- cursor.png is the standard cursor
- cursormiddle.png can be used for an additional center dot

## Important Rendering Notes

1. **Layering**: Elements are rendered in a specific order, with later elements appearing on top
2. **Transparency**: Most elements use PNG transparency for smooth blending
3. **Scaling**: Elements are scaled based on the Circle Size (CS) gameplay setting
4. **Timing**: Approach circles appear ~800ms before the hit time (varies by AR - Approach Rate)
5. **Animation**: Animated elements cycle through frames at specific intervals
6. **HD Elements**: @2x versions (e.g., hitcircle@2x.png) are used for high DPI displays

## Color and Combo System

- Hit circles are colored based on combo colors defined in the beatmap
- The skin provides the texture, but the game applies color tinting
- Combo numbers increment and reset based on the beatmap's combo structure
- New combos start at 1 and use different combo colors

## Common Skin Element Sizes (Default)

- **hitcircle.png**: 128x128 pixels (256x256 for @2x)
- **approachcircle.png**: 126x126 pixels (252x252 for @2x)
- **default numbers**: ~52x65 pixels each
- **cursor.png**: 64x64 pixels typical
- **sliderball.png**: 128x128 pixels

These sizes can vary between skins, but these are common defaults that work well.

## Audio Elements

osu! skins can customize a wide variety of audio files to enhance the gameplay experience. All audio files should be in .wav format unless otherwise noted (some support .mp3/.ogg for longer files).

### Interface Sounds

#### Menu and Navigation
- **heartbeat.wav** - Plays when hovering over the osu! cookie in main menu
- **seeya.wav** - Plays when closing the client (requires osu!supporter)
- **welcome.wav** - Plays when launching the client (requires osu!supporter)
- **menuback.wav** - Menu back button sound
- **menuhit.wav** - Menu selection sound
- **menu-back-click.wav** - Alternative back button click
- **menu-direct-click.wav** - osu!direct click sound
- **menu-edit-click.wav** - Edit button click
- **menu-exit-click.wav** - Exit button click
- **menu-freeplay-click.wav** - Solo/freeplay button click
- **menu-multiplayer-click.wav** - Multiplayer button click
- **menu-options-click.wav** - Options button click
- **menu-play-click.wav** - Play button click
- **back-button-click.wav** - General back button sound
- **click-close.wav** - Window/dialog close sound
- **click-short-confirm.wav** - Short confirmation click

#### Input Sounds
- **key-confirm.wav** - Sending chat messages or confirming input
- **key-delete.wav** - Deleting text
- **key-movement.wav** - Moving the text cursor
- **key-press-1.wav** through **key-press-4.wav** - Key press sounds for chat/search/edit fields

#### UI Elements
- **check-on.wav** - Checkbox checked sound
- **check-off.wav** - Checkbox unchecked sound

### Gameplay Sounds

#### Game Screens
- **applause.wav** - Plays on the ranking screen after clearing a map (supports .mp3/.ogg for longer files)
- **pause-loop.wav** - Loops during pause screens, fades when window loses focus

#### Metronome
- **metronomelow.wav** - Used in beatmap editor, offset wizard, and for osu!catch banana ticks

### Hitsounds

Hitsounds are organized by sampleset. Each sampleset can have its own version of each hitsound:
- **Normal** sampleset (prefix: normal-)
- **Soft** sampleset (prefix: soft-)
- **Drum** sampleset (prefix: drum-)

#### Core Hitsounds (for each sampleset)
- **[sampleset]-hitnormal.wav** - Basic hit sound
- **[sampleset]-hitclap.wav** - Clap addition
- **[sampleset]-hitfinish.wav** - Finish/cymbal addition
- **[sampleset]-hitwhistle.wav** - Whistle addition

#### Slider Sounds (for each sampleset)
- **[sampleset]-slidertick.wav** - Sound when passing over slider ticks
- **[sampleset]-sliderslide.wav** - Continuous sound while sliding (looped)
- **[sampleset]-sliderwhistle.wav** - Whistle sound during sliding (looped)

Example filenames:
- normal-hitnormal.wav, soft-hitnormal.wav, drum-hitnormal.wav
- normal-hitclap.wav, soft-hitclap.wav, drum-hitclap.wav
- etc.

### Spinner Sounds
- **spinnerspin.wav** - Loops during spinner spinning, pitch modulates with speed unless disabled via SpinnerFrequencyModulate
- **spinnerbonus.wav** - Plays when gaining 1000 spinner bonus points
- **spinnerbonus-max.wav** - Lazer-only: plays when maximum spinner score is already reached

### Nightcore Mod Sounds

These sounds only play when the Nightcore mod is active:
- **nightcore-kick.wav** - Plays on beats 1 and 3
- **nightcore-clap.wav** - Plays on beats 2 and 4
- **nightcore-hat.wav** - Plays on every odd quaver (when slider tick rate is multiple of 2)
- **nightcore-finish.wav** - Plays on the first beat of every 4 measures (unless timing point omits barline)

### Audio Format Notes
- Most sounds should be in .wav format for best compatibility
- **applause.wav** can use .mp3 or .ogg format for longer duration
- Keep file sizes reasonable - optimize audio quality vs. file size
- Looped sounds (pause-loop, sliderslide, sliderwhistle, spinnerspin) should loop seamlessly
- Volume levels should be balanced across all sounds to avoid jarring transitions