#!/usr/bin/env python3
"""
Simple hitsound arrangement reviewer
"""

import os
import shutil
import tkinter as tk
from pathlib import Path
import pygame
import json

class SimpleReviewer:
    def __init__(self):
        # Initialize pygame for audio
        pygame.mixer.init()
        
        # Get all arrangements
        self.arrangements = []
        rendered_path = Path("rendered_hitsounds")
        extracted_path = Path("extracted_arrangements")
        
        for folder in rendered_path.iterdir():
            if folder.is_dir():
                combined = folder / "combined.mp3"
                if combined.exists():
                    # Find matching extracted folder
                    extracted = extracted_path / folder.name
                    self.arrangements.append({
                        'name': folder.name,
                        'combined': combined,
                        'rendered': folder,
                        'extracted': extracted if extracted.exists() else None
                    })
        
        self.arrangements.sort(key=lambda x: x['name'])
        self.current = 0
        
        if not self.arrangements:
            print("No arrangements found!")
            exit()
        
        # Create window
        self.root = tk.Tk()
        self.root.title(f"Review Arrangements ({len(self.arrangements)} total)")
        self.root.geometry("600x200")
        
        # Current file label
        self.label = tk.Label(self.root, text="", font=("Arial", 12), wraplength=550)
        self.label.pack(pady=20)
        
        # Buttons frame
        button_frame = tk.Frame(self.root)
        button_frame.pack(pady=20)
        
        # Play button
        self.play_btn = tk.Button(button_frame, text="▶ Play/Stop", command=self.toggle_play,
                                  font=("Arial", 14), width=10, height=2)
        self.play_btn.pack(side=tk.LEFT, padx=10)
        
        # Remove button
        tk.Button(button_frame, text="🗑 Remove", command=self.remove,
                 font=("Arial", 14), width=10, height=2, fg="red").pack(side=tk.LEFT, padx=10)
        
        # Favourite button
        tk.Button(button_frame, text="⭐ Favourite", command=self.favourite,
                 font=("Arial", 14), width=10, height=2, fg="green").pack(side=tk.LEFT, padx=10)
        
        # Skip button
        tk.Button(button_frame, text="→ Skip", command=self.skip,
                 font=("Arial", 14), width=10, height=2).pack(side=tk.LEFT, padx=10)
        
        # Counter label
        self.counter = tk.Label(self.root, text="", font=("Arial", 10))
        self.counter.pack(pady=10)
        
        # Load first
        self.load_current()
        
        # Auto-play on load
        self.play()
    
    def load_current(self):
        """Load current arrangement"""
        if self.current >= len(self.arrangements):
            self.label.config(text="All done!")
            self.counter.config(text="")
            return
        
        arr = self.arrangements[self.current]
        self.label.config(text=arr['name'][:100])
        self.counter.config(text=f"{self.current + 1} / {len(self.arrangements)}")
    
    def play(self):
        """Play current audio"""
        if self.current < len(self.arrangements):
            try:
                pygame.mixer.music.load(str(self.arrangements[self.current]['combined']))
                pygame.mixer.music.play(-1)  # Loop forever
            except:
                pass
    
    def toggle_play(self):
        """Toggle play/pause"""
        if pygame.mixer.music.get_busy():
            pygame.mixer.music.stop()
        else:
            self.play()
    
    def remove(self):
        """Remove current arrangement"""
        if self.current < len(self.arrangements):
            arr = self.arrangements[self.current]
            pygame.mixer.music.stop()
            
            # Delete folders
            try:
                shutil.rmtree(arr['rendered'])
                if arr['extracted'] and arr['extracted'].exists():
                    shutil.rmtree(arr['extracted'])
            except:
                pass
            
            # Remove from list
            self.arrangements.pop(self.current)
            
            # Load next
            if self.current >= len(self.arrangements) and self.current > 0:
                self.current -= 1
            
            self.load_current()
            self.play()  # Auto-play next
    
    def favourite(self):
        """Move to favourites"""
        if self.current < len(self.arrangements):
            arr = self.arrangements[self.current]
            pygame.mixer.music.stop()
            
            # Create favourites folder
            fav_path = Path("favourites")
            fav_path.mkdir(exist_ok=True)
            
            # Move to favourites
            dest = fav_path / arr['name']
            dest.mkdir(exist_ok=True)
            
            try:
                # Copy arrangement.json if exists
                if arr['extracted'] and arr['extracted'].exists():
                    arr_json = arr['extracted'] / "arrangement.json"
                    if arr_json.exists():
                        shutil.copy2(arr_json, dest / "arrangement.json")
                    
                    # Copy original audio
                    audio = arr['extracted'] / "audio.mp3"
                    if audio.exists():
                        shutil.copy2(audio, dest / "audio.mp3")
                
                # Copy hitsounds only
                hitsounds = arr['rendered'] / "hitsounds.mp3"
                if hitsounds.exists():
                    shutil.copy2(hitsounds, dest / "hitsounds.mp3")
                
                # Delete originals
                shutil.rmtree(arr['rendered'])
                if arr['extracted'] and arr['extracted'].exists():
                    shutil.rmtree(arr['extracted'])
            except Exception as e:
                print(f"Error: {e}")
            
            # Remove from list
            self.arrangements.pop(self.current)
            
            # Load next
            if self.current >= len(self.arrangements) and self.current > 0:
                self.current -= 1
            
            self.load_current()
            self.play()  # Auto-play next
    
    def skip(self):
        """Skip to next"""
        pygame.mixer.music.stop()
        self.current += 1
        self.load_current()
        if self.current < len(self.arrangements):
            self.play()  # Auto-play next
    
    def run(self):
        self.root.mainloop()

if __name__ == "__main__":
    app = SimpleReviewer()
    app.run()