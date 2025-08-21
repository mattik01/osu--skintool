package com.osuskin.tool.test;

import com.osuskin.tool.service.SkinIndexCache;
import com.osuskin.tool.service.SkinIndexCache.SkinIndexResult;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DTSkinDebugTest {
    public static void main(String[] args) {
        System.out.println("=== TESTING DT SKIN INDEX BUILDING ===\n");
        
        // The DT skin path
        Path skinPath = Paths.get("/root/skins/-    《DT》 - 『 東方Project 』 Paper Touhou Project  -");
        
        System.out.println("Skin path: " + skinPath);
        System.out.println("Path exists: " + java.nio.file.Files.exists(skinPath));
        
        // Check if index file exists
        Path indexFile = Paths.get(System.getProperty("user.home"), 
            ".config", "OsuSkinTool", "skin-indexes",
            "_root_skins_-    《DT》 - 『 東方Project 』 Paper Touhou Project  --index.json");
        System.out.println("Index file exists: " + java.nio.file.Files.exists(indexFile));
        
        System.out.println("\nAttempting to load/build index...");
        
        SkinIndexCache cache = new SkinIndexCache();
        
        try {
            long start = System.currentTimeMillis();
            SkinIndexResult result = cache.loadOrCreateIndex(skinPath);
            long elapsed = System.currentTimeMillis() - start;
            
            System.out.println("\nSUCCESS!");
            System.out.println("Time taken: " + elapsed + "ms");
            System.out.println("Used cache: " + result.usedCache);
            System.out.println("Elements found: " + result.index.availableElements.size());
            System.out.println("Animations found: " + result.index.animationFrameCounts.size());
            
            // Check if index file was created
            System.out.println("\nAfter operation:");
            System.out.println("Index file exists: " + java.nio.file.Files.exists(indexFile));
            
        } catch (Exception e) {
            System.out.println("\nERROR occurred!");
            System.out.println("Exception type: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n=== TEST COMPLETE ===");
    }
}