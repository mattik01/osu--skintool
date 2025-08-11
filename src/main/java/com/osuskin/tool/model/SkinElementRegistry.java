package com.osuskin.tool.model;

import java.util.*;

/**
 * Registry of all osu! skin elements with categorization and metadata.
 * This class defines which elements belong to which categories and how they should be handled.
 */
public class SkinElementRegistry {
    
    public enum ElementCategory {
        // Gameplay elements
        HIT_CIRCLES("Hit Circles", "Circle elements for gameplay"),
        SLIDERS("Sliders", "Slider track and ball elements"),
        SPINNER("Spinner", "Spinner-related elements"),
        CURSOR("Cursor", "Cursor and cursor trail"),
        APPROACH_CIRCLES("Approach Circles", "Approach circle indicators"),
        HIT_BURSTS("Hit Bursts", "Hit confirmation animations"),
        FOLLOW_POINTS("Follow Points", "Connection lines between objects"),
        
        // Audio categories
        HIT_SOUNDS("Hit Sounds", "Hit confirmation sounds"),
        SLIDER_SOUNDS("Slider Sounds", "Slider slide and tick sounds"),
        SPINNER_SOUNDS("Spinner Sounds", "Spinner bonus sounds"),
        UI_SOUNDS("UI Sounds", "Menu and interface sounds"),
        MISC_SOUNDS("Misc Sounds", "Applause, fail, and other sounds"),
        
        // UI elements
        NUMBERS("Numbers", "Score and combo numbers"),
        HEALTH_BAR("Health Bar", "HP bar elements"),
        SCORE_BAR("Score Bar", "Score display elements"),
        MOD_ICONS("Mod Icons", "Gameplay modifier icons"),
        RANKING_SCREEN("Ranking Screen", "Results screen elements"),
        PAUSE_OVERLAY("Pause Overlay", "Pause and fail screen elements"),
        MENU_ELEMENTS("Menu Elements", "Main menu and song select"),
        
        // Other
        PARTICLES("Particles", "Star and particle effects"),
        LIGHTING("Lighting", "Lighting effects"),
        MISC_IMAGES("Misc Images", "Other visual elements");
        
        private final String displayName;
        private final String description;
        
        ElementCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    public static class ElementDefinition {
        private final String baseName;
        private final ElementCategory category;
        private final boolean isAnimated;
        private final boolean isRequired;
        private final String[] extensions;
        private final String description;
        
        public ElementDefinition(String baseName, ElementCategory category, 
                               boolean isAnimated, boolean isRequired,
                               String[] extensions, String description) {
            this.baseName = baseName;
            this.category = category;
            this.isAnimated = isAnimated;
            this.isRequired = isRequired;
            this.extensions = extensions;
            this.description = description;
        }
        
        public String getBaseName() { return baseName; }
        public ElementCategory getCategory() { return category; }
        public boolean isAnimated() { return isAnimated; }
        public boolean isRequired() { return isRequired; }
        public String[] getExtensions() { return extensions; }
        public String getDescription() { return description; }
    }
    
    private static final Map<String, ElementDefinition> ELEMENT_DEFINITIONS = new HashMap<>();
    private static final String[] IMAGE_EXTENSIONS = {"png", "jpg", "jpeg"};
    private static final String[] AUDIO_EXTENSIONS = {"wav", "ogg", "mp3"};
    
    static {
        // Initialize all element definitions
        initializeHitCircleElements();
        initializeSliderElements();
        initializeSpinnerElements();
        initializeCursorElements();
        initializeHitSounds();
        initializeUISounds();
        initializeNumbers();
        initializeUIElements();
        initializeMiscElements();
    }
    
    private static void initializeHitCircleElements() {
        // Hit circles
        register("hitcircle", ElementCategory.HIT_CIRCLES, false, true, IMAGE_EXTENSIONS,
                "Main hit circle texture");
        register("hitcircleoverlay", ElementCategory.HIT_CIRCLES, true, false, IMAGE_EXTENSIONS,
                "Overlay for hit circles (can be animated)");
        register("hitcircleselect", ElementCategory.HIT_CIRCLES, false, false, IMAGE_EXTENSIONS,
                "Hit circle in editor");
        register("approachcircle", ElementCategory.APPROACH_CIRCLES, false, true, IMAGE_EXTENSIONS,
                "Approach circle that shrinks");
        
        // Hit bursts (hit confirmation)
        register("hit0", ElementCategory.HIT_BURSTS, true, false, IMAGE_EXTENSIONS, "Miss indicator");
        register("hit50", ElementCategory.HIT_BURSTS, true, false, IMAGE_EXTENSIONS, "50 score burst");
        register("hit100", ElementCategory.HIT_BURSTS, true, false, IMAGE_EXTENSIONS, "100 score burst");
        register("hit100k", ElementCategory.HIT_BURSTS, true, false, IMAGE_EXTENSIONS, "100 katu burst");
        register("hit300", ElementCategory.HIT_BURSTS, true, false, IMAGE_EXTENSIONS, "300 score burst");
        register("hit300g", ElementCategory.HIT_BURSTS, true, false, IMAGE_EXTENSIONS, "300 geki burst");
        register("hit300k", ElementCategory.HIT_BURSTS, true, false, IMAGE_EXTENSIONS, "300 katu burst");
        
        // Lighting
        register("lighting", ElementCategory.LIGHTING, false, false, IMAGE_EXTENSIONS,
                "Lighting effect on hit");
        register("particle50", ElementCategory.PARTICLES, false, false, IMAGE_EXTENSIONS, "50 hit particle");
        register("particle100", ElementCategory.PARTICLES, false, false, IMAGE_EXTENSIONS, "100 hit particle");
        register("particle300", ElementCategory.PARTICLES, false, false, IMAGE_EXTENSIONS, "300 hit particle");
    }
    
    private static void initializeSliderElements() {
        // Slider body
        register("sliderb", ElementCategory.SLIDERS, true, true, IMAGE_EXTENSIONS,
                "Slider body/track (can be animated)");
        register("sliderfollowcircle", ElementCategory.SLIDERS, true, false, IMAGE_EXTENSIONS,
                "Circle that follows slider ball");
        register("sliderscorepoint", ElementCategory.SLIDERS, false, false, IMAGE_EXTENSIONS,
                "Slider tick");
        register("sliderpoint10", ElementCategory.SLIDERS, false, false, IMAGE_EXTENSIONS,
                "10 point slider tick");
        register("sliderpoint30", ElementCategory.SLIDERS, false, false, IMAGE_EXTENSIONS,
                "30 point slider tick");
        
        // Slider ball
        register("sliderball", ElementCategory.SLIDERS, true, false, IMAGE_EXTENSIONS,
                "Ball that moves along slider");
        register("sliderball-spec", ElementCategory.SLIDERS, true, false, IMAGE_EXTENSIONS,
                "Spectator slider ball");
        
        // Reverse arrow
        register("reversearrow", ElementCategory.SLIDERS, false, false, IMAGE_EXTENSIONS,
                "Arrow indicating slider reversal");
        
        // Follow points
        register("followpoint", ElementCategory.FOLLOW_POINTS, true, false, IMAGE_EXTENSIONS,
                "Connection between hit objects");
    }
    
    private static void initializeSpinnerElements() {
        register("spinner-background", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Spinner background");
        register("spinner-circle", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Spinner circle");
        register("spinner-metre", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Spinner progress meter");
        register("spinner-osu", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "osu! logo in spinner");
        register("spinner-clear", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Spinner clear indicator");
        register("spinner-spin", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Spin instruction text");
        register("spinner-approachcircle", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Spinner approach indicator");
        register("spinner-rpm", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "RPM indicator background");
        register("spinner-top", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Top part of spinner");
        register("spinner-bottom", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Bottom part of spinner");
        register("spinner-glow", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Spinner glow effect");
        register("spinner-middle", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Middle part of spinner");
        register("spinner-middle2", ElementCategory.SPINNER, false, false, IMAGE_EXTENSIONS,
                "Alternative middle spinner");
    }
    
    private static void initializeCursorElements() {
        register("cursor", ElementCategory.CURSOR, false, true, IMAGE_EXTENSIONS,
                "Main cursor image");
        register("cursortrail", ElementCategory.CURSOR, false, false, IMAGE_EXTENSIONS,
                "Cursor trail effect");
        register("cursormiddle", ElementCategory.CURSOR, false, false, IMAGE_EXTENSIONS,
                "Middle part of cursor");
        register("cursor-smoke", ElementCategory.CURSOR, false, false, IMAGE_EXTENSIONS,
                "Smoke trail when key pressed");
    }
    
    private static void initializeHitSounds() {
        // Normal hit sounds
        register("normal-hitnormal", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Normal hit sound");
        register("normal-hitwhistle", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Whistle hit sound");
        register("normal-hitfinish", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Finish hit sound");
        register("normal-hitclap", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Clap hit sound");
        
        // Soft hit sounds
        register("soft-hitnormal", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Soft hit sound");
        register("soft-hitwhistle", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Soft whistle sound");
        register("soft-hitfinish", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Soft finish sound");
        register("soft-hitclap", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Soft clap sound");
        
        // Drum hit sounds
        register("drum-hitnormal", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Drum hit sound");
        register("drum-hitwhistle", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Drum whistle sound");
        register("drum-hitfinish", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Drum finish sound");
        register("drum-hitclap", ElementCategory.HIT_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Drum clap sound");
        
        // Slider sounds
        register("normal-slidertick", ElementCategory.SLIDER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Normal slider tick");
        register("normal-sliderslide", ElementCategory.SLIDER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Normal slider slide");
        register("normal-sliderwhistle", ElementCategory.SLIDER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Normal slider whistle");
        register("soft-slidertick", ElementCategory.SLIDER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Soft slider tick");
        register("soft-sliderslide", ElementCategory.SLIDER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Soft slider slide");
        register("drum-slidertick", ElementCategory.SLIDER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Drum slider tick");
        register("drum-sliderslide", ElementCategory.SLIDER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Drum slider slide");
        
        // Spinner sounds
        register("spinnerspin", ElementCategory.SPINNER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Spinner spinning sound");
        register("spinnerbonus", ElementCategory.SPINNER_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Spinner bonus sound");
    }
    
    private static void initializeUISounds() {
        // Gameplay UI sounds
        register("combobreak", ElementCategory.MISC_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Combo break sound");
        register("failsound", ElementCategory.MISC_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Fail sound effect");
        register("applause", ElementCategory.MISC_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Applause on pass");
        register("sectionpass", ElementCategory.MISC_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Section pass sound");
        register("sectionfail", ElementCategory.MISC_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Section fail sound");
        
        // Menu sounds
        register("heartbeat", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Low HP heartbeat");
        register("seeya", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Exit sound");
        register("welcome", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Welcome sound");
        register("click-short", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Short click sound");
        register("click-short-confirm", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Confirm click sound");
        register("click-close", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Close click sound");
        register("menuback", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Menu back sound");
        register("menuhit", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Menu hit sound");
        register("menuclick", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS,
                "Menu click sound");
        
        // Countdown
        register("count1s", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS, "Countdown 1");
        register("count2s", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS, "Countdown 2");
        register("count3s", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS, "Countdown 3");
        register("gos", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS, "Go! sound");
        register("readys", ElementCategory.UI_SOUNDS, false, false, AUDIO_EXTENSIONS, "Ready sound");
    }
    
    private static void initializeNumbers() {
        // Default numbers
        register("default-0", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 0");
        register("default-1", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 1");
        register("default-2", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 2");
        register("default-3", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 3");
        register("default-4", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 4");
        register("default-5", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 5");
        register("default-6", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 6");
        register("default-7", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 7");
        register("default-8", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 8");
        register("default-9", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Number 9");
        
        // Score numbers
        register("score-0", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 0");
        register("score-1", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 1");
        register("score-2", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 2");
        register("score-3", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 3");
        register("score-4", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 4");
        register("score-5", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 5");
        register("score-6", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 6");
        register("score-7", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 7");
        register("score-8", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 8");
        register("score-9", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score 9");
        register("score-comma", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score comma");
        register("score-dot", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score dot");
        register("score-percent", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score percent");
        register("score-x", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Score x");
        
        // Combo numbers
        for (int i = 0; i <= 9; i++) {
            register("combo-" + i, ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS,
                    "Combo number " + i);
        }
        register("combo-comma", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Combo comma");
        register("combo-x", ElementCategory.NUMBERS, false, false, IMAGE_EXTENSIONS, "Combo x");
    }
    
    private static void initializeUIElements() {
        // Health bar
        register("scorebar-bg", ElementCategory.HEALTH_BAR, false, false, IMAGE_EXTENSIONS,
                "Score bar background");
        register("scorebar-colour", ElementCategory.HEALTH_BAR, true, false, IMAGE_EXTENSIONS,
                "Health bar fill");
        register("scorebar-ki", ElementCategory.HEALTH_BAR, false, false, IMAGE_EXTENSIONS,
                "Health bar danger");
        register("scorebar-kidanger", ElementCategory.HEALTH_BAR, false, false, IMAGE_EXTENSIONS,
                "Health bar critical");
        register("scorebar-kidanger2", ElementCategory.HEALTH_BAR, false, false, IMAGE_EXTENSIONS,
                "Health bar critical alt");
        register("scorebar-marker", ElementCategory.HEALTH_BAR, false, false, IMAGE_EXTENSIONS,
                "Health bar marker");
        
        // Pause/Fail overlay
        register("pause-overlay", ElementCategory.PAUSE_OVERLAY, false, false, IMAGE_EXTENSIONS,
                "Pause screen overlay");
        register("fail-background", ElementCategory.PAUSE_OVERLAY, false, false, IMAGE_EXTENSIONS,
                "Fail screen background");
        register("pause-back", ElementCategory.PAUSE_OVERLAY, false, false, IMAGE_EXTENSIONS,
                "Pause back button");
        register("pause-continue", ElementCategory.PAUSE_OVERLAY, false, false, IMAGE_EXTENSIONS,
                "Pause continue button");
        register("pause-retry", ElementCategory.PAUSE_OVERLAY, false, false, IMAGE_EXTENSIONS,
                "Pause retry button");
        
        // Ranking screen
        register("ranking-panel", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Ranking panel background");
        register("ranking-perfect", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Perfect rank");
        register("ranking-S", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS, "S rank");
        register("ranking-S-small", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Small S rank");
        register("ranking-SH", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS, "S silver rank");
        register("ranking-SH-small", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Small S silver");
        register("ranking-X", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS, "SS rank");
        register("ranking-X-small", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Small SS rank");
        register("ranking-XH", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "SS silver rank");
        register("ranking-XH-small", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Small SS silver");
        register("ranking-A", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS, "A rank");
        register("ranking-A-small", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Small A rank");
        register("ranking-B", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS, "B rank");
        register("ranking-B-small", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Small B rank");
        register("ranking-C", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS, "C rank");
        register("ranking-C-small", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Small C rank");
        register("ranking-D", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS, "D rank");
        register("ranking-D-small", ElementCategory.RANKING_SCREEN, false, false, IMAGE_EXTENSIONS,
                "Small D rank");
        
        // Mod icons
        register("selection-mod-easy", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS, "Easy mod");
        register("selection-mod-nofail", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS, "No fail mod");
        register("selection-mod-halftime", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Half time mod");
        register("selection-mod-hardrock", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Hard rock mod");
        register("selection-mod-suddendeath", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Sudden death mod");
        register("selection-mod-perfect", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Perfect mod");
        register("selection-mod-doubletime", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Double time mod");
        register("selection-mod-nightcore", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Nightcore mod");
        register("selection-mod-hidden", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Hidden mod");
        register("selection-mod-flashlight", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Flashlight mod");
        register("selection-mod-relax", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS, "Relax mod");
        register("selection-mod-autopilot", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Autopilot mod");
        register("selection-mod-spunout", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Spun out mod");
        register("selection-mod-autoplay", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Autoplay mod");
        register("selection-mod-cinema", ElementCategory.MOD_ICONS, false, false, IMAGE_EXTENSIONS,
                "Cinema mod");
    }
    
    private static void initializeMiscElements() {
        // Menu elements
        register("menu-background", ElementCategory.MENU_ELEMENTS, false, false, IMAGE_EXTENSIONS,
                "Menu background");
        register("welcome_text", ElementCategory.MENU_ELEMENTS, false, false, IMAGE_EXTENSIONS,
                "Welcome text");
        register("menu-snow", ElementCategory.MENU_ELEMENTS, false, false, IMAGE_EXTENSIONS,
                "Menu snow effect");
        register("star", ElementCategory.PARTICLES, false, false, IMAGE_EXTENSIONS, "Star particle");
        register("star2", ElementCategory.PARTICLES, false, false, IMAGE_EXTENSIONS, "Star particle alt");
        
        // Gameplay UI
        register("inputoverlay-background", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Input overlay background");
        register("inputoverlay-key", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Input overlay key");
        register("play-skip", ElementCategory.MISC_IMAGES, true, false, IMAGE_EXTENSIONS,
                "Skip button");
        register("play-unranked", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Unranked indicator");
        register("arrow-pause", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Pause arrow");
        register("arrow-warning", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Warning arrow");
        register("multi-skipped", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Multiplayer skip indicator");
        register("section-pass", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Section pass indicator");
        register("section-fail", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Section fail indicator");
        
        // Countdown
        register("ready", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS, "Ready text");
        register("count1", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS, "Countdown 1");
        register("count2", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS, "Countdown 2");
        register("count3", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS, "Countdown 3");
        register("go", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS, "Go text");
        
        // Comboburst
        register("comboburst", ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                "Combo burst image");
        // Support multiple combobursts
        for (int i = 0; i < 10; i++) {
            register("comboburst-" + i, ElementCategory.MISC_IMAGES, false, false, IMAGE_EXTENSIONS,
                    "Combo burst " + i);
        }
    }
    
    private static void register(String baseName, ElementCategory category, boolean isAnimated,
                                boolean isRequired, String[] extensions, String description) {
        ELEMENT_DEFINITIONS.put(baseName.toLowerCase(), 
            new ElementDefinition(baseName, category, isAnimated, isRequired, extensions, description));
    }
    
    public static ElementDefinition getDefinition(String elementName) {
        // Remove extension and frame number if present
        String baseName = elementName.toLowerCase();
        
        // Remove extension
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }
        
        // Remove frame number (-0, -1, -2, etc.)
        if (baseName.matches(".*-\\d+$")) {
            baseName = baseName.replaceAll("-\\d+$", "");
        }
        
        // Remove @2x suffix for HD elements
        baseName = baseName.replace("@2x", "");
        
        return ELEMENT_DEFINITIONS.get(baseName);
    }
    
    public static Map<ElementCategory, List<ElementDefinition>> getElementsByCategory() {
        Map<ElementCategory, List<ElementDefinition>> result = new HashMap<>();
        
        for (ElementDefinition def : ELEMENT_DEFINITIONS.values()) {
            result.computeIfAbsent(def.getCategory(), k -> new ArrayList<>()).add(def);
        }
        
        return result;
    }
    
    public static List<ElementDefinition> getRequiredElements() {
        return ELEMENT_DEFINITIONS.values().stream()
            .filter(ElementDefinition::isRequired)
            .toList();
    }
    
    public static List<ElementDefinition> getAnimatedElements() {
        return ELEMENT_DEFINITIONS.values().stream()
            .filter(ElementDefinition::isAnimated)
            .toList();
    }
    
    public static boolean isValidSkinElement(String fileName) {
        return getDefinition(fileName) != null;
    }
}