package com.example.csc325_firebase_webview_auth.view;

import com.example.csc325_firebase_webview_auth.model.FirebaseService;
import com.example.csc325_firebase_webview_auth.model.UserSession;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicBoolean;

public class App extends Application {
    private static Stage stage;
    private static final FirebaseService FIREBASE = FirebaseService.getInstance();
    private final AtomicBoolean splashElapsed = new AtomicBoolean(false);
    private final AtomicBoolean firebaseFinished = new AtomicBoolean(false);

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("Student Records Cloud");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        showScene(new SplashView().getRoot(), 1040, 680);
        stage.show();

        PauseTransition minimumSplash = new PauseTransition(Duration.seconds(1.4));
        minimumSplash.setOnFinished(event -> {
            splashElapsed.set(true);
            openAuthWhenReady();
        });
        minimumSplash.play();

        FIREBASE.initializeAsync().whenComplete((connected, error) -> Platform.runLater(() -> {
            firebaseFinished.set(true);
            openAuthWhenReady();
        }));
    }

    private void openAuthWhenReady() {
        if (splashElapsed.get() && firebaseFinished.get()) {
            showAuth();
        }
    }

    public static void showAuth() {
        stage.setTitle("Student Records Cloud · Account");
        showScene(new AuthView(FIREBASE).getRoot(), 1040, 680);
    }

    public static void showDashboard(UserSession session) {
        stage.setTitle("Student Records Cloud · Dashboard");
        showScene(new DashboardView(FIREBASE, session).getRoot(), 1120, 720);
    }

    public static void exit() {
        Platform.exit();
    }

    private static void showScene(javafx.scene.Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(App.class.getResource("/files/styles.css").toExternalForm());
        stage.setScene(scene);
    }

    @Override
    public void stop() {
        FIREBASE.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
