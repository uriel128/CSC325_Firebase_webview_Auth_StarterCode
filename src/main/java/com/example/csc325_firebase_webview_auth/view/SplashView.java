package com.example.csc325_firebase_webview_auth.view;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class SplashView {
    private final StackPane root = new StackPane();

    public SplashView() {
        Label mark = new Label("SR");
        mark.getStyleClass().add("splash-mark");
        Label title = new Label("Student Records Cloud");
        title.getStyleClass().add("splash-title");
        Label subtitle = new Label("Secure records, available anywhere");
        subtitle.getStyleClass().add("splash-subtitle");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(34, 34);

        VBox content = new VBox(14, mark, title, subtitle, progress);
        content.setAlignment(Pos.CENTER);
        root.getStyleClass().add("splash");
        root.getChildren().add(content);
    }

    public Parent getRoot() {
        return root;
    }
}
