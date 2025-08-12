package com.osuskin.tool.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum ElementGroup {
    CIRCLES("Circles", 
        "hitcircle", "hitcircleoverlay", "approachcircle", 
        "reversearrow", "sliderstartcircle", "sliderstartcircleoverlay",
        "sliderscorepoint", "sliderendcircle", "sliderendcircleoverlay"),
    
    CURSOR("Cursor",
        "cursor", "cursortrail", "cursormiddle", "cursor-smoke"),
    
    UI("UI",
        "scorebar-bg", "scorebar-colour", "scorebar-ki", "scorebar-kidanger",
        "scorebar-kidanger2", "scorebar-marker", "score-0", "score-1", 
        "score-2", "score-3", "score-4", "score-5", "score-6", "score-7",
        "score-8", "score-9", "score-comma", "score-dot", "score-percent",
        "score-x", "ranking-S", "ranking-S-small", "ranking-A", "ranking-A-small",
        "ranking-B", "ranking-B-small", "ranking-C", "ranking-C-small",
        "ranking-D", "ranking-D-small", "ranking-X", "ranking-X-small",
        "ranking-SH", "ranking-SH-small", "ranking-XH", "ranking-XH-small",
        "pause-overlay", "fail-background", "pause-back", "pause-continue",
        "pause-retry", "button-left", "button-middle", "button-right",
        "menu-back", "menu-button-background", "selection-mode", "selection-mods",
        "selection-random", "selection-options", "mode-osu", "mode-taiko",
        "mode-fruits", "mode-mania"),
    
    REST("Rest",
        "sliderb", "sliderb0", "sliderb1", "sliderb2", "sliderb3", "sliderb4",
        "sliderb5", "sliderb6", "sliderb7", "sliderb8", "sliderb9",
        "sliderfollowcircle", "slidertrack", "spinner-approachcircle",
        "spinner-rpm", "spinner-clear", "spinner-spin", "spinner-circle",
        "spinner-background", "spinner-metre", "spinner-bottom", "spinner-top",
        "spinner-middle", "spinner-middle2", "spinner-glow", "hit0", "hit50",
        "hit100", "hit100k", "hit300", "hit300g", "hit300k", "hit50-0",
        "hit100-0", "hit300-0", "particle50", "particle100", "particle300",
        "lighting", "followpoint", "followpoint-0", "followpoint-1",
        "followpoint-2", "followpoint-3", "followpoint-4", "followpoint-5",
        "followpoint-6", "followpoint-7", "play-skip", "play-unranked",
        "play-warningarrow", "arrow-pause", "arrow-warning", "section-pass",
        "section-fail", "count1", "count2", "count3", "go", "ready",
        "comboburst", "comboburst-0", "comboburst-1", "comboburst-2",
        "comboburst-3", "comboburst-4", "comboburst-5", "comboburst-6",
        "comboburst-7", "star", "star2"),
    
    HITSOUNDS("HitSounds",
        "normal-hitnormal", "normal-hitclap", "normal-hitfinish", "normal-hitwhistle",
        "normal-slidertick", "normal-sliderslide", "normal-sliderwhistle",
        "soft-hitnormal", "soft-hitclap", "soft-hitfinish", "soft-hitwhistle",
        "soft-slidertick", "soft-sliderslide", "soft-sliderwhistle",
        "drum-hitnormal", "drum-hitclap", "drum-hitfinish", "drum-hitwhistle",
        "drum-slidertick", "drum-sliderslide", "drum-sliderwhistle",
        "taiko-normal-hitnormal", "taiko-normal-hitclap", "taiko-normal-hitfinish",
        "taiko-normal-hitwhistle", "taiko-soft-hitnormal", "taiko-soft-hitclap",
        "taiko-soft-hitfinish", "taiko-soft-hitwhistle", "taiko-drum-hitnormal",
        "taiko-drum-hitclap", "taiko-drum-hitfinish", "taiko-drum-hitwhistle"),
    
    REST_AUDIO("Rest-Audio",
        "combobreak", "comboburst", "sectionpass", "sectionfail",
        "applause", "failsound", "pause-loop", "pause-back-click",
        "pause-continue-click", "pause-retry-click", "pause-hover",
        "click-short", "click-short-confirm", "click-close", "menuback",
        "menuclick", "menuhit", "welcome", "seeya", "menu-char-select",
        "menu-direct-click", "menu-edit-click", "menu-exit-click",
        "menu-freeplay-click", "menu-multiplayer-click", "menu-options-click",
        "menu-play-click", "menu-charts-click", "shutter", "count1s",
        "count2s", "count3s", "gos", "readys", "match-confirm", "match-join",
        "match-leave", "match-notready", "match-ready", "match-start",
        "nightcore-clap", "nightcore-finish", "nightcore-hat", "nightcore-kick",
        "spinnerbonus", "spinnerspin", "spinnerfall", "key-confirm",
        "key-delete", "key-movement", "key-press-1", "key-press-2",
        "key-press-3", "key-press-4", "check-on", "check-off", "select-expand",
        "select-difficulty", "back-button-click", "back-button-hover");

    private final String displayName;
    private final Set<String> elementPrefixes;

    ElementGroup(String displayName, String... prefixes) {
        this.displayName = displayName;
        this.elementPrefixes = new HashSet<>(Arrays.asList(prefixes));
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean containsElement(String elementName) {
        if (elementName == null) return false;
        String lowerName = elementName.toLowerCase();
        
        // For REST group, include anything NOT in other groups
        if (this == REST) {
            // Check if this element belongs to any other group
            for (ElementGroup group : ElementGroup.values()) {
                if (group != REST && group != REST_AUDIO && group.containsElementDirect(lowerName)) {
                    return false;
                }
            }
            // If it's not audio and not in any other group, it belongs to REST
            return !isAudioFile(elementName);
        }
        
        // For REST_AUDIO group, include any audio NOT in HITSOUNDS
        if (this == REST_AUDIO) {
            if (!isAudioFile(elementName)) return false;
            return !HITSOUNDS.containsElementDirect(lowerName);
        }
        
        return containsElementDirect(lowerName);
    }
    
    private boolean containsElementDirect(String lowerName) {
        for (String prefix : elementPrefixes) {
            if (lowerName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isAudioFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".ogg");
    }

    public Set<String> getElementPrefixes() {
        return new HashSet<>(elementPrefixes);
    }
}