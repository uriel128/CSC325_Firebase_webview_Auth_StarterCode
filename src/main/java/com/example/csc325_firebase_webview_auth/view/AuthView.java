package com.example.csc325_firebase_webview_auth.view;

import com.example.csc325_firebase_webview_auth.model.FirebaseService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class AuthView {
    private final FirebaseService firebase;
    private final BorderPane root = new BorderPane();
    private final TabPane tabs = new TabPane();
    private final Label status = new Label();

    public AuthView(FirebaseService firebase) {
        this.firebase = firebase;
        root.getStyleClass().add("app-shell");
        root.setTop(createMenu());
        root.setCenter(createContent());
    }

    public Parent getRoot() {
        return root;
    }

    private MenuBar createMenu() {
        MenuItem signIn = new MenuItem("Sign In");
        signIn.setOnAction(event -> tabs.getSelectionModel().select(0));
        MenuItem register = new MenuItem("Register");
        register.setOnAction(event -> tabs.getSelectionModel().select(1));
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(event -> App.exit());

        Menu account = new Menu("Account");
        account.getItems().addAll(signIn, register, new SeparatorMenuItem(), exit);

        MenuItem about = new MenuItem("About");
        about.setOnAction(event -> alert(Alert.AlertType.INFORMATION, "About",
                "Student Records Cloud\nJavaFX with Firebase Auth, Firestore, and Storage."));
        Menu help = new Menu("Help");
        help.getItems().add(about);

        MenuBar menu = new MenuBar(account, help);
        menu.getStyleClass().add("main-menu");
        return menu;
    }

    private Pane createContent() {
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(new Tab("Sign In", signInForm()), new Tab("Create Account", registrationForm()));
        tabs.getStyleClass().add("auth-tabs");
        tabs.setMaxWidth(470);

        Label title = new Label("Student Records Cloud");
        title.getStyleClass().add("screen-title");
        Label subtitle = new Label("Sign in to manage records and your Firebase profile.");
        subtitle.getStyleClass().add("screen-subtitle");

        status.setText(firebase.getStatus());
        status.setWrapText(true);
        status.getStyleClass().add(firebase.isCloudReady() ? "status-success" : "status-warning");

        VBox card = new VBox(10, title, subtitle, tabs, status);
        card.setPadding(new Insets(28));
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(520);

        StackPane wrapper = new StackPane(card);
        wrapper.setPadding(new Insets(42));
        return wrapper;
    }

    private VBox signInForm() {
        TextField email = field("Email address");
        PasswordField password = passwordField("Password");
        Button submit = primaryButton("Sign In");
        ProgressIndicator progress = progress();

        submit.setOnAction(event -> {
            if (email.getText().isBlank() || password.getText().isBlank()) {
                setStatus("Enter your email and password.", false);
                return;
            }
            setBusy(submit, progress, true);
            firebase.signIn(email.getText(), password.getText()).whenComplete((session, error) ->
                    Platform.runLater(() -> {
                        setBusy(submit, progress, false);
                        if (error != null) {
                            setStatus(FirebaseService.rootMessage(error), false);
                        } else {
                            App.showDashboard(session);
                        }
                    }));
        });
        password.setOnAction(event -> submit.fire());

        HBox action = new HBox(10, submit, progress);
        action.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(12, label("Email"), email, label("Password"), password, action);
        form.setPadding(new Insets(18, 4, 10, 4));
        return form;
    }

    private VBox registrationForm() {
        TextField name = field("Full name");
        TextField email = field("Email address");
        PasswordField password = passwordField("At least 6 characters");
        PasswordField confirm = passwordField("Repeat password");
        Button submit = primaryButton("Create Account");
        ProgressIndicator progress = progress();

        submit.setOnAction(event -> {
            if (name.getText().isBlank() || email.getText().isBlank() || password.getText().isBlank()) {
                setStatus("Complete every registration field.", false);
                return;
            }
            if (password.getText().length() < 6) {
                setStatus("Password must contain at least 6 characters.", false);
                return;
            }
            if (!password.getText().equals(confirm.getText())) {
                setStatus("Passwords do not match.", false);
                return;
            }

            setBusy(submit, progress, true);
            firebase.register(email.getText(), password.getText(), name.getText()).whenComplete((session, error) ->
                    Platform.runLater(() -> {
                        setBusy(submit, progress, false);
                        if (error != null) {
                            setStatus(FirebaseService.rootMessage(error), false);
                        } else {
                            App.showDashboard(session);
                        }
                    }));
        });

        HBox action = new HBox(10, submit, progress);
        action.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(10,
                label("Full name"), name,
                label("Email"), email,
                label("Password"), password,
                label("Confirm password"), confirm,
                action);
        form.setPadding(new Insets(18, 4, 10, 4));
        return form;
    }

    private void setStatus(String message, boolean success) {
        status.setText(message);
        status.getStyleClass().removeAll("status-success", "status-warning");
        status.getStyleClass().add(success ? "status-success" : "status-warning");
    }

    private static void setBusy(Button button, ProgressIndicator progress, boolean busy) {
        button.setDisable(busy);
        progress.setVisible(busy);
    }

    private static TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("form-field");
        return field;
    }

    private static PasswordField passwordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.getStyleClass().add("form-field");
        return field;
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private static ProgressIndicator progress() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(26, 26);
        indicator.setVisible(false);
        return indicator;
    }

    private static void alert(Alert.AlertType type, String title, String text) {
        Alert alert = new Alert(type, text, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
