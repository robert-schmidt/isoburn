package com.isoburn;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.InputStream;

public class IsoBurnApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private static HostServices hostServices;

    public static void main(String[] args) {
        launch(args);
    }

    public static HostServices getAppHostServices() {
        return hostServices;
    }

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(SpringBootApp.class)
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        hostServices = getHostServices();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        loader.setControllerFactory(springContext::getBean);

        Parent root = loader.load();
        Scene scene = new Scene(root);

        // Set application icon (JavaFX handles dock icon automatically)
        try {
            InputStream iconStream = getClass().getResourceAsStream("/icons/isoburn.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                primaryStage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("Could not load application icon: " + e.getMessage());
        }

        primaryStage.setTitle("isoBURN - ISO to USB Burner");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(450);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
        });
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }

    @org.springframework.boot.autoconfigure.SpringBootApplication
    @org.springframework.context.annotation.ComponentScan(basePackages = "com.isoburn")
    public static class SpringBootApp {
    }
}
