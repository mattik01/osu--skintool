package com.osuskin.tool.test;

import com.osuskin.tool.model.Skin;
import com.osuskin.tool.service.AsyncSkinLoader;
import com.osuskin.tool.service.AsyncSkinLoader.SkinLoadResult;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DTSkinTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== TESTING DT PROJECT SKIN LOADING ===\n");
        
        // The problematic skin path
        Path skinPath = Paths.get("/root/skins/-    《DT》 - 『 東方Project 』 Paper Touhou Project  -");
        
        // Create a skin object
        Skin skin = new Skin();
        skin.setName("DT Project PaperTouhou");
        skin.setDirectoryPath(skinPath.toString());
        
        System.out.println("Testing skin: " + skin.getName());
        System.out.println("Path: " + skinPath);
        System.out.println("Path exists: " + java.nio.file.Files.exists(skinPath));
        System.out.println();
        
        // Test with AsyncSkinLoader
        AsyncSkinLoader loader = new AsyncSkinLoader();
        
        try {
            System.out.println("Creating load task...");
            Task<SkinLoadResult> task = loader.createLoadTask(skin);
            
            // Set up event handlers
            task.setOnSucceeded(e -> {
                SkinLoadResult result = task.getValue();
                if (result != null) {
                    System.out.println("Task succeeded!");
                    System.out.println("  Success: " + result.success);
                    System.out.println("  Load time: " + result.loadTimeMs + "ms");
                    if (!result.success) {
                        System.out.println("  Error: " + result.errorMessage);
                    }
                } else {
                    System.out.println("Task succeeded but result is NULL!");
                }
            });
            
            task.setOnFailed(e -> {
                System.out.println("Task FAILED!");
                Throwable ex = task.getException();
                if (ex != null) {
                    System.out.println("Exception: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
            
            // Run the task
            System.out.println("Running task...");
            Thread thread = new Thread(task);
            thread.start();
            thread.join(); // Wait for completion
            
            // Check the result
            SkinLoadResult result = task.getValue();
            if (result == null) {
                System.out.println("\n❌ PROBLEM FOUND: Task.getValue() returns NULL!");
                System.out.println("This is why the UI shows 'Unknown error loading skin'");
                
                // Check task state
                System.out.println("Task state: " + task.getState());
                if (task.getException() != null) {
                    System.out.println("Task exception: " + task.getException());
                }
            } else {
                System.out.println("\n✓ Result obtained successfully");
            }
            
        } catch (Exception e) {
            System.out.println("Error during test: " + e.getMessage());
            e.printStackTrace();
        } finally {
            loader.shutdown();
        }
        
        System.out.println("\n=== TEST COMPLETE ===");
    }
}