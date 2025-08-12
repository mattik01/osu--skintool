package com.osuskin.tool;

import com.osuskin.tool.controller.MainController;
import com.osuskin.tool.service.ConfigurationService;
import com.osuskin.tool.util.ConfigurationManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class OsuSkinToolApplication extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(OsuSkinToolApplication.class);
    private static final String APP_TITLE = "osu! Skin Selection Tool";
    private static final String MAIN_FXML = "/fxml/main.fxml";
    private static final String APP_CSS = "/css/application.css";
    
    private ConfigurationManager configurationManager;
    
    @Override
    public void init() throws Exception {
        super.init();
        logger.info("Initializing osu! Skin Selection Tool");
        
        // Initialize configuration manager
        configurationManager = new ConfigurationManager();
        configurationManager.loadConfiguration();
        
        logger.info("Application initialization completed");
    }
    
    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Starting main application window");
            
            // Load FXML
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(MAIN_FXML));
            Scene scene = new Scene(fxmlLoader.load());
            
            // Get controller and inject dependencies
            MainController controller = fxmlLoader.getController();
            controller.setConfigurationManager(configurationManager);
            
            // Apply CSS
            scene.getStylesheets().add(getClass().getResource(APP_CSS).toExternalForm());
            
            // Configure stage
            primaryStage.setTitle(APP_TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(600);
            primaryStage.setWidth(1400);
            primaryStage.setHeight(800);
            
            // Set application icon (if available)
            try {
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/app-icon.png")));
            } catch (Exception e) {
                logger.warn("Could not load application icon: {}", e.getMessage());
            }
            
            // Handle close request
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Application shutdown requested");
                shutdown();
            });
            
            primaryStage.show();
            logger.info("Main window displayed successfully");
            
        } catch (IOException e) {
            logger.error("Failed to load main application window", e);
            showErrorAndExit("Failed to start application: " + e.getMessage());
        }
    }
    
    @Override
    public void stop() throws Exception {
        shutdown();
        super.stop();
    }
    
    private void shutdown() {
        try {
            logger.info("Shutting down application");
            if (configurationManager != null) {
                configurationManager.saveConfiguration();
            }
            logger.info("Application shutdown completed");
        } catch (Exception e) {
            logger.error("Error during application shutdown", e);
        }
    }
    
    private void showErrorAndExit(String message) {
        logger.error("Critical error: {}", message);
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Application Error");
        alert.setContentText(message);
        alert.showAndWait();
        System.exit(1);
    }
    
    public static void main(String[] args) {
        // Set system properties for better performance
        System.setProperty("javafx.preloader", "com.osuskin.tool.preloader.SkinToolPreloader");
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        
        logger.info("Launching osu! Skin Selection Tool");
        launch(args);
    }
}