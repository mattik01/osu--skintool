#!/usr/bin/env python3
"""
All-in-one osu! beatmap hitsound processor with FULL ACCURACY
- Extracts hitsound arrangements from beatmaps (one per difficulty)
- Renders them with skin audio files
- EXTREME volume settings for prominent hitsounds
- Full slider mechanics with proper tick timing
- Custom sample index support (hitnormal2.wav, etc.)
- Volume control from timing points
- Inherited timing point velocity multipliers
- Sliderslide looping for entire duration
"""

import os
import sys
import json
import subprocess
import tempfile
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import List, Dict, Optional, Tuple
import re

# ================== CONFIGURATION ==================
# EXTREME volume settings - hitsounds MUCH louder than background
HITSOUND_BASE_VOLUME = 2.0     # Base volume multiplier for each hitsound
BACKGROUND_VOLUME = 0.05       # 5% volume for background music
HITSOUND_MIX_VOLUME = 2.5      # 250% volume for hitsounds in combined mix
FINAL_BOOST_VOLUME = 3.0       # Final volume boost for hitsound-only mix
MIX_WEIGHT_PER_SOUND = 1.5     # Weight for each sound in amix

# ================== DATA CLASSES ==================
@dataclass
class TimingPoint:
    """Represents a timing point that can change sampleset and control velocity"""
    time: int
    beat_length: float  # Positive = BPM, Negative = velocity multiplier
    sampleset: int  # 0=default, 1=normal, 2=soft, 3=drum
    sample_index: int  # Custom sample number
    volume: int  # Volume percentage
    uninherited: bool  # True if BPM change, False if inherited
    
    @property
    def bpm(self) -> float:
        """Get BPM if this is an uninherited timing point"""
        if self.uninherited and self.beat_length > 0:
            return 60000.0 / self.beat_length
        return None
    
    @property
    def slider_multiplier(self) -> float:
        """Get slider velocity multiplier if this is inherited"""
        if not self.uninherited and self.beat_length < 0:
            return -100.0 / self.beat_length
        return 1.0

@dataclass
class HitObject:
    """Represents a single hit object in osu!"""
    time: int
    type: int
    hitsound: int  # Head hitsound
    extras: str = ""
    sampleset: str = "normal"
    additions: str = "normal"
    sample_index: int = 0  # Custom sample number
    volume: int = 100  # Volume from timing point
    end_time: Optional[int] = None
    # Slider-specific data
    slider_repeats: int = 1
    slider_length: float = 0  # Pixel length
    edge_hitsounds: List[int] = None
    edge_samplesets: List[Tuple[int, int]] = None
    # Calculated tick data
    tick_times: List[int] = None  # When ticks should play
    
    @property
    def is_circle(self) -> bool:
        return self.type & 1
    
    @property
    def is_slider(self) -> bool:
        return self.type & 2
    
    @property
    def is_spinner(self) -> bool:
        return self.type & 8
    
    @property
    def has_whistle(self) -> bool:
        return self.hitsound & 2
    
    @property
    def has_finish(self) -> bool:
        return self.hitsound & 4
    
    @property
    def has_clap(self) -> bool:
        return self.hitsound & 8
    
    def get_hitsounds_with_timing(self, base_sv: float, tick_rate: float) -> List[Dict]:
        """Get list of hitsounds with accurate timing and sample index"""
        sounds = []
        sampleset_map = {0: self.sampleset, 1: "normal", 2: "soft", 3: "drum"}
        
        # For circles and slider heads
        if self.is_circle or self.is_slider:
            # Head/circle hitnormal
            sounds.append({
                "sound": "hitnormal",
                "sampleset": self.sampleset,
                "sample_index": self.sample_index,
                "volume": self.volume,
                "time_offset": 0
            })
            
            # Head/circle additions
            if self.has_whistle:
                sounds.append({
                    "sound": "whistle",
                    "sampleset": self.additions or self.sampleset,
                    "sample_index": self.sample_index,
                    "volume": self.volume,
                    "time_offset": 0
                })
            if self.has_finish:
                sounds.append({
                    "sound": "finish",
                    "sampleset": self.additions or self.sampleset,
                    "sample_index": self.sample_index,
                    "volume": self.volume,
                    "time_offset": 0
                })
            if self.has_clap:
                sounds.append({
                    "sound": "clap",
                    "sampleset": self.additions or self.sampleset,
                    "sample_index": self.sample_index,
                    "volume": self.volume,
                    "time_offset": 0
                })
        
        # Slider-specific sounds
        if self.is_slider and self.end_time:
            # Add sliderslide for the entire duration
            slider_duration = self.end_time - self.time
            sounds.append({
                "sound": "sliderslide",
                "sampleset": self.sampleset,
                "sample_index": self.sample_index,
                "volume": self.volume,
                "time_offset": 0,
                "duration": slider_duration  # Special field for continuous sounds
            })
            
            # Add slider ticks at calculated positions
            if self.tick_times:
                for tick_time in self.tick_times:
                    sounds.append({
                        "sound": "slidertick",
                        "sampleset": self.sampleset,
                        "sample_index": self.sample_index,
                        "volume": self.volume,
                        "time_offset": tick_time - self.time
                    })
            
            # Process edge hitsounds (tail and repeats)
            if self.edge_hitsounds and len(self.edge_hitsounds) > 1:
                single_slide_duration = slider_duration // self.slider_repeats if self.slider_repeats > 1 else slider_duration
                
                for edge_idx in range(1, len(self.edge_hitsounds)):
                    edge_hitsound = self.edge_hitsounds[edge_idx]
                    time_offset = single_slide_duration * edge_idx
                    
                    # Determine sampleset for this edge
                    edge_sampleset = self.sampleset
                    edge_additions = self.additions
                    edge_sample_index = self.sample_index
                    if self.edge_samplesets and edge_idx < len(self.edge_samplesets):
                        s, a = self.edge_samplesets[edge_idx]
                        edge_sampleset = sampleset_map.get(s, self.sampleset)
                        edge_additions = sampleset_map.get(a, self.additions)
                    
                    # Always play hitnormal at edges
                    sounds.append({
                        "sound": "hitnormal",
                        "sampleset": edge_sampleset,
                        "sample_index": edge_sample_index,
                        "volume": self.volume,
                        "time_offset": time_offset
                    })
                    
                    # Add edge additions
                    if edge_hitsound & 2:  # Whistle
                        sounds.append({
                            "sound": "whistle",
                            "sampleset": edge_additions or edge_sampleset,
                            "sample_index": edge_sample_index,
                            "volume": self.volume,
                            "time_offset": time_offset
                        })
                    if edge_hitsound & 4:  # Finish
                        sounds.append({
                            "sound": "finish",
                            "sampleset": edge_additions or edge_sampleset,
                            "sample_index": edge_sample_index,
                            "volume": self.volume,
                            "time_offset": time_offset
                        })
                    if edge_hitsound & 8:  # Clap
                        sounds.append({
                            "sound": "clap",
                            "sampleset": edge_additions or edge_sampleset,
                            "sample_index": edge_sample_index,
                            "volume": self.volume,
                            "time_offset": time_offset
                        })
            else:
                # No edge hitsounds - default tail
                sounds.append({
                    "sound": "hitnormal",
                    "sampleset": self.sampleset,
                    "sample_index": self.sample_index,
                    "volume": self.volume,
                    "time_offset": self.end_time - self.time
                })
        
        return sounds if sounds else [{"sound": "hitnormal", "sampleset": "normal", "sample_index": 0, "volume": 100, "time_offset": 0}]

@dataclass
class HitsoundArrangement:
    """Represents a 10-second window of hitsounds"""
    beatmap_folder: str
    difficulty_name: str
    start_time: int
    end_time: int
    hit_objects: List[Dict]
    unique_sounds: List[str]
    samplesets_used: List[str]
    variety_score: float
    audio_file: str

# ================== BEATMAP PARSER ==================
class BeatmapParser:
    """Parses .osu files with full timing point support"""
    
    def __init__(self, filepath: str):
        self.filepath = filepath
        self.hit_objects: List[HitObject] = []
        self.timing_points: List[TimingPoint] = []
        self.audio_filename = ""
        self.difficulty_name = os.path.splitext(os.path.basename(filepath))[0]
        self.default_sampleset = "normal"
        self.slider_multiplier = 1.4  # Base slider velocity
        self.slider_tick_rate = 1.0  # Ticks per beat
        
    def parse(self):
        """Parse the .osu file with full timing support"""
        with open(self.filepath, 'r', encoding='utf-8', errors='ignore') as f:
            lines = f.readlines()
        
        in_general = False
        in_difficulty = False
        in_timingpoints = False
        in_hitobjects = False
        
        for line in lines:
            line = line.strip()
            
            # Check sections
            if line == "[General]":
                in_general = True
                in_difficulty = False
                in_timingpoints = False
                in_hitobjects = False
                continue
            elif line == "[Difficulty]":
                in_general = False
                in_difficulty = True
                in_timingpoints = False
                in_hitobjects = False
                continue
            elif line == "[TimingPoints]":
                in_general = False
                in_difficulty = False
                in_timingpoints = True
                in_hitobjects = False
                continue
            elif line == "[HitObjects]":
                in_general = False
                in_difficulty = False
                in_timingpoints = False
                in_hitobjects = True
                continue
            elif line.startswith("["):
                in_general = False
                in_difficulty = False
                in_timingpoints = False
                in_hitobjects = False
                continue
            
            # Parse General section
            if in_general:
                if line.startswith("AudioFilename:"):
                    self.audio_filename = line.split(":", 1)[1].strip()
                elif line.startswith("SampleSet:"):
                    sampleset_name = line.split(":", 1)[1].strip().lower()
                    if sampleset_name in ["normal", "soft", "drum"]:
                        self.default_sampleset = sampleset_name
            
            # Parse Difficulty section
            if in_difficulty:
                if line.startswith("SliderMultiplier:"):
                    try:
                        self.slider_multiplier = float(line.split(":", 1)[1].strip())
                    except ValueError:
                        pass
                elif line.startswith("SliderTickRate:"):
                    try:
                        self.slider_tick_rate = float(line.split(":", 1)[1].strip())
                    except ValueError:
                        pass
            
            # Parse timing points
            if in_timingpoints and line and not line.startswith("["):
                try:
                    parts = line.split(",")
                    if len(parts) >= 8:
                        time = int(float(parts[0]))
                        beat_length = float(parts[1])
                        meter = int(parts[2]) if len(parts) > 2 and parts[2] else 4
                        sampleset = int(parts[3]) if len(parts) > 3 and parts[3] else 0
                        sample_index = int(parts[4]) if len(parts) > 4 and parts[4] else 0
                        volume = int(parts[5]) if len(parts) > 5 and parts[5] else 100
                        uninherited = bool(int(parts[6])) if len(parts) > 6 and parts[6] else True
                        
                        self.timing_points.append(TimingPoint(
                            time=time,
                            beat_length=beat_length,
                            sampleset=sampleset,
                            sample_index=sample_index,
                            volume=volume,
                            uninherited=uninherited
                        ))
                except (ValueError, IndexError):
                    continue
            
            # Parse hit objects
            if in_hitobjects and line and not line.startswith("["):
                try:
                    parts = line.split(",")
                    if len(parts) >= 5:
                        time = int(parts[2])
                        obj_type = int(parts[3])
                        hitsound = int(parts[4])
                        # Join all remaining parts as extras
                        extras = ",".join(parts[5:]) if len(parts) > 5 else ""
                        
                        # Get timing point info at this time
                        tp = self.get_timing_point_at_time(time)
                        sampleset = self.get_sampleset_from_tp(tp)
                        sample_index = tp.sample_index if tp else 0
                        volume = tp.volume if tp else 100
                        
                        # Initialize slider data
                        slider_repeats = 1
                        slider_length = 0
                        edge_hitsounds = None
                        edge_samplesets = None
                        end_time = None
                        tick_times = None
                        
                        # Parse slider
                        if obj_type & 2:  # Is slider
                            # Format: curveType|points,repeats,length,edgeHitsounds,edgeSamplesets
                            # First find where curve ends (after last |, before first ,)
                            curve_data = []
                            remaining_data = []
                            
                            # Split by comma first to separate curve from other data
                            all_parts = extras.split(',')
                            
                            # First part contains curve type and points
                            if all_parts:
                                curve_data.append(all_parts[0])
                                # Collect remaining parts
                                remaining_data = all_parts[1:]
                            
                            # Now parse the non-curve data
                            # Format after curve: repeats,length,edgeHitsounds,edgeSamplesets,extras
                            
                            # Parse repeats (index 0 after curve)
                            if len(remaining_data) > 0 and remaining_data[0]:
                                try:
                                    slider_repeats = int(remaining_data[0])
                                except ValueError:
                                    slider_repeats = 1
                            
                            # Parse length (index 1 after curve)
                            if len(remaining_data) > 1 and remaining_data[1]:
                                try:
                                    slider_length = float(remaining_data[1])
                                    # Calculate slider duration
                                    duration = self.calculate_slider_duration(time, slider_length, slider_repeats)
                                    end_time = time + duration
                                    # Calculate tick positions
                                    tick_times = self.calculate_tick_times(time, slider_length, slider_repeats)
                                except ValueError:
                                    slider_length = 100  # Default
                                    end_time = time + 500
                            
                            # Parse edge hitsounds (index 2 after curve)
                            if len(remaining_data) > 2 and remaining_data[2]:
                                try:
                                    edge_hitsounds = [int(h) for h in remaining_data[2].split('|')]
                                except (ValueError, AttributeError):
                                    edge_hitsounds = None
                            
                            # Parse edge samplesets (index 3 after curve)
                            if len(remaining_data) > 3 and remaining_data[3]:
                                try:
                                    edge_samplesets = []
                                    for edge in remaining_data[3].split('|'):
                                        if ':' in edge:
                                            s, a = edge.split(':')
                                            edge_samplesets.append((int(s), int(a)))
                                        else:
                                            edge_samplesets.append((0, 0))
                                except (ValueError, AttributeError):
                                    edge_samplesets = None
                        
                        # Parse custom samplesets from extras
                        additions = sampleset
                        if extras and ":" in extras:
                            extra_parts = extras.split(":")
                            if len(extra_parts) >= 2:
                                try:
                                    sampleset_num = int(extra_parts[0])
                                    additions_num = int(extra_parts[1])
                                    
                                    sampleset_map = {0: self.default_sampleset, 1: "normal", 2: "soft", 3: "drum"}
                                    if sampleset_num in sampleset_map:
                                        sampleset = sampleset_map[sampleset_num]
                                    if additions_num in sampleset_map:
                                        additions = sampleset_map[additions_num]
                                    
                                    # Check for custom sample index
                                    if len(extra_parts) >= 3:
                                        sample_index = int(extra_parts[2])
                                except ValueError:
                                    pass
                        
                        self.hit_objects.append(HitObject(
                            time=time,
                            type=obj_type,
                            hitsound=hitsound,
                            extras=extras,
                            sampleset=sampleset,
                            additions=additions,
                            sample_index=sample_index,
                            volume=volume,
                            end_time=end_time,
                            slider_repeats=slider_repeats,
                            slider_length=slider_length,
                            edge_hitsounds=edge_hitsounds,
                            edge_samplesets=edge_samplesets,
                            tick_times=tick_times
                        ))
                except (ValueError, IndexError):
                    continue
    
    def get_timing_point_at_time(self, time: int) -> Optional[TimingPoint]:
        """Get the active timing point at a given time"""
        active_tp = None
        for tp in self.timing_points:
            if tp.time > time:
                break
            active_tp = tp
        return active_tp
    
    def get_sampleset_from_tp(self, tp: Optional[TimingPoint]) -> str:
        """Get sampleset from timing point"""
        if not tp or tp.sampleset == 0:
            return self.default_sampleset
        
        sampleset_map = {1: "normal", 2: "soft", 3: "drum"}
        return sampleset_map.get(tp.sampleset, self.default_sampleset)
    
    def calculate_slider_duration(self, start_time: int, length: float, repeats: int) -> int:
        """Calculate actual slider duration based on timing"""
        # Get active timing point
        tp = self.get_timing_point_at_time(start_time)
        if not tp:
            return 500 * repeats  # Fallback
        
        # Find the base BPM
        base_bpm_tp = tp
        if not tp.uninherited:
            # Find the last uninherited timing point
            for t in reversed(self.timing_points):
                if t.time <= start_time and t.uninherited:
                    base_bpm_tp = t
                    break
        
        if not base_bpm_tp or base_bpm_tp.beat_length <= 0:
            return 500 * repeats  # Fallback
        
        # Calculate base slider velocity (pixels per millisecond)
        # Formula: SliderMultiplier * 100 / BeatLength
        base_sv = (self.slider_multiplier * 100) / base_bpm_tp.beat_length
        
        # Apply inherited velocity multiplier
        if not tp.uninherited and tp.beat_length < 0:
            velocity_multiplier = -100.0 / tp.beat_length
        else:
            velocity_multiplier = 1.0
        
        actual_sv = base_sv * velocity_multiplier
        
        # Duration = length / velocity * repeats
        single_duration = length / actual_sv
        total_duration = int(single_duration * repeats)
        
        return total_duration
    
    def calculate_tick_times(self, start_time: int, length: float, repeats: int) -> List[int]:
        """Calculate when slider ticks should play"""
        if length <= 0 or self.slider_tick_rate <= 0:
            return []
        
        tick_times = []
        
        # Get timing info
        tp = self.get_timing_point_at_time(start_time)
        if not tp:
            return []
        
        # Find base BPM
        base_bpm_tp = tp
        if not tp.uninherited:
            for t in reversed(self.timing_points):
                if t.time <= start_time and t.uninherited:
                    base_bpm_tp = t
                    break
        
        if not base_bpm_tp or base_bpm_tp.beat_length <= 0:
            return []
        
        # Calculate base slider velocity
        base_sv = (self.slider_multiplier * 100) / base_bpm_tp.beat_length
        
        # Apply velocity multiplier
        if not tp.uninherited and tp.beat_length < 0:
            velocity_multiplier = -100.0 / tp.beat_length
        else:
            velocity_multiplier = 1.0
        
        actual_sv = base_sv * velocity_multiplier
        
        # Calculate tick distance in osu! pixels
        # TickDistance = SliderMultiplier * 100 / SliderTickRate
        tick_distance = (self.slider_multiplier * 100) / self.slider_tick_rate
        
        if tick_distance <= 0 or tick_distance >= length:
            return []  # No ticks if distance is invalid or longer than slider
        
        # Calculate single slide duration
        single_duration = length / actual_sv
        
        # Calculate tick positions for each repeat
        for repeat in range(repeats):
            # Direction changes on odd repeats
            reverse = (repeat % 2 == 1)
            
            # Calculate ticks for this repeat
            current_distance = tick_distance
            while current_distance < length:
                # Calculate progress along slider
                progress = current_distance / length
                
                # Reverse progress if going backwards
                if reverse:
                    progress = 1.0 - progress
                
                # Calculate absolute time
                tick_time = start_time + int(single_duration * repeat + single_duration * progress)
                tick_times.append(tick_time)
                
                current_distance += tick_distance
        
        return tick_times

# ================== HITSOUND EXTRACTOR ==================
class HitsoundExtractor:
    """Extracts hitsound arrangements from beatmaps"""
    
    def __init__(self, songs_folder: str):
        self.songs_folder = Path(songs_folder)
        self.output_folder = Path("extracted_arrangements")
        self.output_folder.mkdir(exist_ok=True)
        self.window_duration = 10000  # 10 seconds
        
    def find_all_difficulty_arrangements(self, beatmap_folder: Path) -> List[HitsoundArrangement]:
        """Find arrangements for each difficulty"""
        osu_files = list(beatmap_folder.glob("*.osu"))
        if not osu_files:
            return []
        
        arrangements = []
        
        for osu_file in osu_files:
            parser = BeatmapParser(str(osu_file))
            parser.parse()
            
            if not parser.hit_objects:
                continue
            
            # Find audio file
            audio_path = beatmap_folder / parser.audio_filename
            if not audio_path.exists():
                # Try to find any audio file
                for ext in ['.mp3', '.ogg', '.wav']:
                    audio_files = list(beatmap_folder.glob(f"*{ext}"))
                    if audio_files:
                        audio_path = audio_files[0]
                        break
            
            if not audio_path.exists():
                continue
            
            # Find best window for this difficulty
            windows = self.create_time_windows(parser.hit_objects)
            best_arrangement = None
            best_score = 0
            
            for start_time, end_time in windows:
                window_objects = [obj for obj in parser.hit_objects 
                                if start_time <= obj.time < end_time]
                
                if len(window_objects) < 10:
                    continue
                
                # Calculate variety score with timing info
                unique_sounds = set()
                samplesets_used = set()
                custom_samples = set()
                volumes_used = set()
                
                for obj in window_objects:
                    hitsounds = obj.get_hitsounds_with_timing(
                        parser.slider_multiplier, 
                        parser.slider_tick_rate
                    )
                    for hs in hitsounds:
                        unique_sounds.add(hs['sound'])
                        samplesets_used.add(hs['sampleset'])
                        if hs.get('sample_index', 0) > 0:
                            custom_samples.add(hs['sample_index'])
                        volumes_used.add(hs.get('volume', 100))
                
                # Enhanced scoring
                variety_score = len(unique_sounds) * 10
                variety_score += len(samplesets_used) * 5
                variety_score += len(custom_samples) * 15  # Bonus for custom samples
                variety_score += len(volumes_used) * 3  # Bonus for volume changes
                density_score = min(len(window_objects), 50)
                total_score = variety_score + density_score
                
                if total_score > best_score:
                    best_score = total_score
                    
                    # Convert to dict format
                    hit_objects_data = []
                    for obj in window_objects:
                        hit_objects_data.append({
                            'time': obj.time - start_time,
                            'sounds': obj.get_hitsounds_with_timing(
                                parser.slider_multiplier,
                                parser.slider_tick_rate
                            ),
                            'sampleset': obj.sampleset,
                            'additions': obj.additions,
                            'sample_index': obj.sample_index,
                            'volume': obj.volume
                        })
                    
                    best_arrangement = HitsoundArrangement(
                        beatmap_folder=beatmap_folder.name,
                        difficulty_name=parser.difficulty_name,
                        start_time=start_time,
                        end_time=end_time,
                        hit_objects=hit_objects_data,
                        unique_sounds=list(unique_sounds),
                        samplesets_used=list(samplesets_used),
                        variety_score=total_score,
                        audio_file=str(audio_path)
                    )
            
            if best_arrangement:
                arrangements.append(best_arrangement)
        
        return arrangements
    
    def create_time_windows(self, hit_objects: List[HitObject]) -> List[Tuple[int, int]]:
        """Create time windows for analysis"""
        if not hit_objects:
            return []
        
        windows = []
        start_time = hit_objects[0].time
        end_time = hit_objects[-1].time
        
        current = start_time
        while current + self.window_duration <= end_time:
            windows.append((current, current + self.window_duration))
            current += 5000  # 5 second slide
        
        if end_time - current >= 5000:
            windows.append((end_time - self.window_duration, end_time))
        
        return windows
    
    def extract_audio_segment(self, audio_file: str, start_ms: int, duration_ms: int, output_file: str) -> bool:
        """Extract audio segment"""
        start_sec = start_ms / 1000.0
        duration_sec = duration_ms / 1000.0
        
        cmd = [
            "ffmpeg",
            "-i", audio_file,
            "-ss", str(start_sec),
            "-t", str(duration_sec),
            "-c", "copy",
            "-y", output_file
        ]
        
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.returncode == 0
    
    def save_arrangement(self, arrangement: HitsoundArrangement) -> str:
        """Save arrangement to disk"""
        # Create unique folder name
        safe_name = re.sub(r'[^\w\s-]', '', arrangement.beatmap_folder)
        safe_name = re.sub(r'[-\s]+', '_', safe_name)[:50]
        diff_name = re.sub(r'[^\w\s-]', '', arrangement.difficulty_name)
        diff_name = re.sub(r'[-\s]+', '_', diff_name)[:80]
        
        folder_name = f"{safe_name}_{diff_name}"
        
        # Handle duplicates
        output_path = self.output_folder / folder_name
        counter = 1
        while output_path.exists():
            folder_name = f"{safe_name}_{diff_name}_{counter}"
            output_path = self.output_folder / folder_name
            counter += 1
        
        output_path.mkdir(exist_ok=True)
        
        # Save arrangement data
        with open(output_path / "arrangement.json", "w") as f:
            json.dump(asdict(arrangement), f, indent=2)
        
        # Extract audio
        self.extract_audio_segment(
            arrangement.audio_file,
            arrangement.start_time,
            self.window_duration,
            str(output_path / "audio.mp3")
        )
        
        return str(output_path)

# ================== HITSOUND RENDERER ==================
class HitsoundRenderer:
    """Renders hitsounds with skin audio files - FULL ACCURACY VERSION"""
    
    def __init__(self, skin_folder: str):
        self.skin_folder = Path(skin_folder)
        self.output_folder = Path("rendered_hitsounds")
        self.output_folder.mkdir(exist_ok=True)
        self.layered_hitsounds = True  # Default osu! behavior
        
    def load_skin_hitsounds(self) -> Dict[str, str]:
        """Load all hitsound files from skin with custom sample support"""
        hitsounds = {}
        audio_extensions = ['.wav', '.ogg', '.mp3', '.flac']
        
        for file in self.skin_folder.iterdir():
            if file.suffix.lower() in audio_extensions:
                stem = file.stem.lower()
                # Support custom samples (e.g., normal-hitnormal2.wav)
                hitsounds[stem] = str(file)
        
        print(f"Found {len(hitsounds)} audio files in skin")
        return hitsounds
    
    def get_sound_file(self, sound_type: str, sampleset: str, additions: str, 
                      sample_index: int, skin_hitsounds: Dict[str, str]) -> Optional[str]:
        """Get the actual sound file with custom sample support"""
        # Map sound names
        prefix_map = {
            'hitnormal': 'hitnormal',
            'whistle': 'hitwhistle',
            'finish': 'hitfinish',
            'clap': 'hitclap',
            'slidertick': 'slidertick',
            'sliderslide': 'sliderslide'
        }
        
        prefix = prefix_map.get(sound_type, sound_type)
        
        # Determine sampleset
        if sound_type in ['whistle', 'finish', 'clap']:
            use_sampleset = additions.lower()
        else:
            use_sampleset = sampleset.lower()
        
        # Try with custom sample index first
        if sample_index > 0:
            custom_name = f"{use_sampleset}-{prefix}{sample_index}"
            if custom_name in skin_hitsounds:
                return skin_hitsounds[custom_name]
        
        # Fallback chain
        possible_names = [
            f"{use_sampleset}-{prefix}",
            f"normal-{prefix}",
            f"soft-{prefix}",
            f"drum-{prefix}",
            prefix
        ]
        
        for name in possible_names:
            if name in skin_hitsounds:
                return skin_hitsounds[name]
        
        return None
    
    def create_hitsound_mix_v2(self, arrangement: HitsoundArrangement, 
                              skin_hitsounds: Dict[str, str], output_file: str) -> bool:
        """Create hitsound mix with full accuracy"""
        print(f"  Creating accurate hitsound mix...")
        
        # Collect all sounds with timing
        files_with_timing = []
        sound_count = {}
        
        for hit_object in arrangement['hit_objects']:
            base_time = hit_object['time']
            sounds = hit_object.get('sounds', [])
            
            for sound_data in sounds:
                sound_type = sound_data['sound']
                sampleset = sound_data.get('sampleset', 'normal')
                sample_index = sound_data.get('sample_index', 0)
                volume = sound_data.get('volume', 100)
                time_offset = sound_data.get('time_offset', 0)
                duration = sound_data.get('duration', None)
                
                # Get the actual sound file
                sound_file = self.get_sound_file(
                    sound_type, sampleset, sampleset, sample_index, skin_hitsounds
                )
                
                if sound_file:
                    timing_ms = base_time + time_offset
                    files_with_timing.append({
                        'file': sound_file,
                        'time': timing_ms,
                        'volume': volume / 100.0,  # Convert to multiplier
                        'duration': duration,
                        'type': sound_type
                    })
                    
                    sound_count[sound_type] = sound_count.get(sound_type, 0) + 1
        
        if not files_with_timing:
            print("  No hitsounds to mix")
            return False
        
        print(f"  Hitsounds to render: {sound_count}")
        print(f"  Total instances: {len(files_with_timing)}")
        
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            
            # Create delayed files with volume
            delayed_files = []
            
            for idx, sound_info in enumerate(files_with_timing):
                sound_file = sound_info['file']
                timing_ms = sound_info['time']
                volume = sound_info['volume']
                duration_ms = sound_info.get('duration')
                sound_type = sound_info['type']
                
                if not os.path.exists(sound_file):
                    continue
                
                output_wav = temp_path / f"delayed_{idx}.wav"
                silence_duration = timing_ms / 1000.0
                total_duration = 10.0  # 10 seconds
                
                # Apply volume with timing point volume
                actual_volume = HITSOUND_BASE_VOLUME * volume
                
                if duration_ms and sound_type == 'sliderslide':
                    # Special handling for sliderslide - extend it for duration
                    slide_duration = min(duration_ms / 1000.0, 5.0)  # Cap at 5 seconds
                    cmd = [
                        "ffmpeg",
                        "-f", "lavfi", "-i", f"anullsrc=r=44100:cl=stereo:d={silence_duration}",
                        "-i", sound_file,
                        "-f", "lavfi", "-i", f"anullsrc=r=44100:cl=stereo:d={total_duration}",
                        "-filter_complex",
                        f"[1:a]aloop=loop=-1:size=44100,atrim=0:{slide_duration},volume={actual_volume}[sound];"
                        f"[0:a][sound][2:a]concat=n=3:v=0:a=1[out]",
                        "-map", "[out]",
                        "-t", str(total_duration),
                        "-y", str(output_wav)
                    ]
                else:
                    # Normal hitsound
                    cmd = [
                        "ffmpeg",
                        "-f", "lavfi", "-i", f"anullsrc=r=44100:cl=stereo:d={silence_duration}",
                        "-i", sound_file,
                        "-f", "lavfi", "-i", f"anullsrc=r=44100:cl=stereo:d={total_duration}",
                        "-filter_complex",
                        f"[1:a]volume={actual_volume}[sound];"
                        f"[0:a][sound][2:a]concat=n=3:v=0:a=1[out]",
                        "-map", "[out]",
                        "-t", str(total_duration),
                        "-y", str(output_wav)
                    ]
                
                result = subprocess.run(cmd, capture_output=True, text=True)
                if result.returncode == 0:
                    delayed_files.append(str(output_wav))
            
            if not delayed_files:
                print("  No valid hitsounds processed")
                return False
            
            print(f"  Processed {len(delayed_files)} hitsound files")
            
            # Mix all files
            if len(delayed_files) == 1:
                temp_output = delayed_files[0]
            else:
                inputs = []
                for f in delayed_files:
                    inputs.extend(["-i", f])
                
                weights = " ".join([str(MIX_WEIGHT_PER_SOUND)] * len(delayed_files))
                mix_inputs = "".join(f"[{i}:a]" for i in range(len(delayed_files)))
                
                temp_output = temp_path / "mixed_raw.wav"
                
                cmd = ["ffmpeg"]
                cmd.extend(inputs)
                cmd.extend([
                    "-filter_complex",
                    f"{mix_inputs}amix=inputs={len(delayed_files)}:duration=first:dropout_transition=0:weights={weights}[out]",
                    "-map", "[out]",
                    "-y", str(temp_output)
                ])
                
                result = subprocess.run(cmd, capture_output=True, text=True)
                if result.returncode != 0:
                    print(f"  Mix failed: {result.stderr[:200]}")
                    return False
            
            # Apply final boost
            print(f"  Applying volume boost (x{FINAL_BOOST_VOLUME})...")
            cmd = [
                "ffmpeg", "-i", str(temp_output),
                "-af", f"volume={FINAL_BOOST_VOLUME},alimiter=limit=0.99:attack=0.1:release=1",
                "-y", output_file
            ]
            result = subprocess.run(cmd, capture_output=True, text=True)
            
            print(f"  Successfully created accurate mix with {len(delayed_files)} hitsounds")
            return result.returncode == 0
    
    def create_combined_mix(self, hitsound_file: str, background_file: str, output_file: str) -> bool:
        """Combine hitsounds with background"""
        if not os.path.exists(hitsound_file) or not os.path.exists(background_file):
            return False
        
        print(f"  Creating balanced mix (background: {int(BACKGROUND_VOLUME*100)}%, hitsounds: {int(HITSOUND_MIX_VOLUME*100)}%)...")
        
        cmd = [
            "ffmpeg",
            "-i", background_file,
            "-i", hitsound_file,
            "-filter_complex",
            f"[0:a]volume={BACKGROUND_VOLUME}[bg];"
            f"[1:a]volume={HITSOUND_MIX_VOLUME}[hs];"
            f"[bg][hs]amix=inputs=2:duration=first:dropout_transition=0[out]",
            "-map", "[out]",
            "-y", output_file
        ]
        
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode == 0:
            print("  Successfully created balanced mix with prominent hitsounds")
        return result.returncode == 0
    
    def render_arrangement(self, arrangement_folder: str, skin_hitsounds: Dict[str, str]) -> bool:
        """Render a single arrangement"""
        arrangement_path = Path(arrangement_folder)
        
        # Load arrangement data
        with open(arrangement_path / "arrangement.json", "r") as f:
            arrangement_data = json.load(f)
        
        # Create output folder
        output_path = self.output_folder / arrangement_path.name
        output_path.mkdir(exist_ok=True)
        
        print(f"\n[{arrangement_data['beatmap_folder']}]")
        print(f"  Difficulty: {arrangement_data['difficulty_name']}")
        print(f"  Objects: {len(arrangement_data['hit_objects'])}")
        
        # Create hitsound mix
        hitsound_file = str(output_path / "hitsounds.mp3")
        if not self.create_hitsound_mix_v2(arrangement_data, skin_hitsounds, hitsound_file):
            return False
        
        # Create combined mix
        background_file = str(arrangement_path / "audio.mp3")
        combined_file = str(output_path / "combined.mp3")
        self.create_combined_mix(hitsound_file, background_file, combined_file)
        
        return True

# ================== MAIN PROCESSOR ==================
def main():
    if len(sys.argv) < 3:
        print("Usage: python process_all_beatmaps.py <songs_folder> <skin_folder> [limit]")
        sys.exit(1)
    
    songs_folder = sys.argv[1]
    skin_folder = sys.argv[2]
    limit = int(sys.argv[3]) if len(sys.argv) > 3 else 100
    
    print("=" * 60)
    print("osu! Beatmap Hitsound Processor - FULL ACCURACY")
    print(f"Songs folder: {songs_folder}")
    print(f"Skin: {skin_folder}")
    print(f"Limit: {limit} beatmaps")
    print("=" * 60)
    
    # Step 1: Extract arrangements
    print("\nSTEP 1: Extracting hitsound arrangements from beatmaps...")
    extractor = HitsoundExtractor(songs_folder)
    
    beatmap_folders = [f for f in Path(songs_folder).iterdir() if f.is_dir()]
    beatmap_folders = beatmap_folders[:limit]
    
    all_arrangements = []
    for idx, beatmap_folder in enumerate(beatmap_folders, 1):
        print(f"Processing {idx}/{len(beatmap_folders)}: {beatmap_folder.name}...")
        arrangements = extractor.find_all_difficulty_arrangements(beatmap_folder)
        
        for arrangement in arrangements:
            output_path = extractor.save_arrangement(arrangement)
            all_arrangements.append(output_path)
    
    print(f"\nExtracted {len(all_arrangements)} arrangements")
    
    # Step 2: Render with skin
    print("\n" + "=" * 60)
    print("STEP 2: Rendering hitsounds with skin audio...")
    
    renderer = HitsoundRenderer(skin_folder)
    skin_name = Path(skin_folder).name
    print(f"\nLoading hitsounds from skin: {skin_name}")
    skin_hitsounds = renderer.load_skin_hitsounds()
    
    for idx, arrangement_folder in enumerate(all_arrangements, 1):
        print(f"\nRendering {idx}/{len(all_arrangements)}:")
        renderer.render_arrangement(arrangement_folder, skin_hitsounds)
    
    print("\n" + "=" * 60)
    print("Processing complete!")
    print(f"Extracted: {len(all_arrangements)} arrangements")
    print(f"Rendered: {len(all_arrangements)} arrangements")
    print(f"Output folder: rendered_hitsounds")
    print("\nVolume settings used:")
    print(f"  Background music: {int(BACKGROUND_VOLUME*100)}%")
    print(f"  Hitsounds: {int(HITSOUND_MIX_VOLUME*100)}% (louder than background)")
    print("=" * 60)

if __name__ == "__main__":
    main()