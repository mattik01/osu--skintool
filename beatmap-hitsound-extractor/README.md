# osu! Beatmap Hitsound Extractor

A comprehensive tool for extracting and rendering hitsound arrangements from osu! beatmaps with custom skins. Features full accuracy hitsound rendering with proper layering, slider mechanics, and timing point support.

## Features

- **Full Accuracy Rendering**: Implements osu!'s complete hitsound layering system
- **Advanced Slider Support**: Proper tick timing, independent edge hitsounds, looping sliderslide
- **Custom Samples**: Supports numbered samples (hitnormal2.wav, etc.)
- **Volume Control**: Respects timing point volumes and inherited multipliers
- **Per-Difficulty Extraction**: Processes each difficulty separately
- **Interactive Review**: GUI tool for auditioning and organizing arrangements

## Setup

1. Place osu! beatmap folders in the `beatmaps/` directory
2. Ensure you have a skin folder with hitsound files
3. Install dependencies:
   ```bash
   sudo apt install python3-tk python3-pygame ffmpeg x11-apps
   ```
4. For WSL users, set display: `export DISPLAY=:0`

## Main Usage

```bash
# Process beatmaps with a skin
python3 process_all_beatmaps.py "beatmaps" "/path/to/skin" [limit]

# Or use the convenience script
./run_all.sh

# Review and organize results
python3 simple_review.py
```

## Scripts Overview

### Core Processing
- `process_all_beatmaps.py` - Main extraction and rendering with full accuracy
- `simple_review.py` - GUI for reviewing and filtering arrangements
- `run_all.sh` - Convenience script for processing

### Organization Tools
- `rename_favourites.py` - Clean up arrangement folder names
- `song_names_only.py` - Remove artist names, keep only song titles
- `add_combined_to_favourites.py` - Create mixed audio files
- `final_rename.py` - Remove numbering from deduplicated folders

## How it Works

The processing pipeline:
1. **Extraction Phase**:
   - Parses .osu files with full timing point support
   - Calculates slider durations and tick positions using BPM/velocity
   - Identifies 10-second windows with maximum variety
   - Extracts one arrangement per difficulty

2. **Rendering Phase**:
   - Loads skin hitsound files (including custom samples)
   - Layers multiple sounds simultaneously (hitnormal + additions)
   - Applies timing point volumes to each sound
   - Creates hitsounds-only and combined versions

3. **Review Phase**:
   - Interactive GUI for listening to arrangements
   - Delete unwanted samples
   - Move favorites to organized folder
   - Auto-play with looping

## Output Structure

```
extracted_arrangements/
├── {beatmap}_{difficulty}/
│   ├── audio.mp3           # 10-second cropped audio
│   └── arrangement.json    # Hitsound timing data
└── summary.json            # Overview of all extractions
```

## Arrangement JSON Format

```json
{
  "beatmap_folder": "beatmap_name",
  "difficulty_name": "Hard",
  "start_time": 45000,
  "end_time": 55000,
  "hit_objects": [
    {
      "time": 0,
      "sounds": ["hitnormal", "whistle"],
      "type": "circle"
    }
  ],
  "unique_sounds": ["hitnormal", "whistle", "finish", "clap"],
  "variety_score": 145.5
}
```

## Variety Score Algorithm

The script calculates a variety score based on:
- Number of unique hitsounds (×10 points)
- Object density (up to 50 points)
- Sound distribution balance
- Bonus for having all basic hitsounds (+20 points)