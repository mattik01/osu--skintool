package com.osuskin.tool.test;

import com.osuskin.tool.model.Skin;
import com.osuskin.tool.service.AsyncSkinLoader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MessageTest extends Application {
    
    @Override
    public void start(Stage primaryStage) {
        System.out.println("=== TESTING PROGRESS MESSAGES ===\n");
        
        // Create test skin
        Path skinPath = Paths.get("/root/skins/BubbleSkin23-10-24");
        Skin testSkin = new Skin();
        testSkin.setName("BubbleSkin23-10-24");
        testSkin.setDirectoryPath(skinPath.toString());
        
        AsyncSkinLoader loader = new AsyncSkinLoader();
        
        // Test 1: First load (might build or use cache)
        System.out.println("Test 1: First load");
        Task<AsyncSkinLoader.SkinLoadResult> task1 = loader.createLoadTask(testSkin);
        
        task1.messageProperty().addListener((obs, old, msg) -> {
            if (msg != null && (msg.contains("index") || msg.contains("Index"))) {
                System.out.println("  Message: " + msg);
            }
        });
        
        Thread thread1 = new Thread(task1);
        thread1.start();
        
        try {
            thread1.join(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Test 2: Second load (should definitely use cache)
        System.out.println("\nTest 2: Second load (same skin)");
        Task<AsyncSkinLoader.SkinLoadResult> task2 = loader.createLoadTask(testSkin);
        
        task2.messageProperty().addListener((obs, old, msg) -> {
            if (msg != null && (msg.contains("index") || msg.contains("Index"))) {
                System.out.println("  Message: " + msg);
            }
        });
        
        Thread thread2 = new Thread(task2);
        thread2.start();
        
        try {
            thread2.join(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("\n✅ Test complete - check messages above");
        Platform.exit();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}