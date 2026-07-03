package com.example.csc325_firebase_webview_auth.view;

import com.example.csc325_firebase_webview_auth.model.FirebaseService;
import com.example.csc325_firebase_webview_auth.model.Person;
import com.example.csc325_firebase_webview_auth.model.UserSession;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Locale;

public final class DashboardView {
    private final FirebaseService firebase;
    private final UserSession session;
    private final BorderPane root = new BorderPane();
    private final ObservableList<Person> records = FXCollections.observableArrayList();
    private final FilteredList<Person> filtered = new FilteredList<>(records, person -> true);
    private final TableView<Person> table = new TableView<>(filtered);
    private final TextField nameField = field("Student name");
    private final TextField majorField = field("Major");
    private final TextField ageField = field("Age");
    private final TextField searchField = field("Search name or major...");
    private final Label status = new Label("Ready");
    private final Label count = new Label("0 records");
    private final ImageView profileImage = new ImageView();
    private Button saveButton;

    public DashboardView(FirebaseService firebase, UserSession session) {
        this.firebase = firebase;
        this.session = session;
        configureTable();
        configureSearch();

        root.getStyleClass().add("app-shell");
        root.setTop(new VBox(createMenu(), createHeader()));
        root.setCenter(createWorkspace());
        root.setBottom(createStatusBar());
        refreshRecords();
        loadProfilePicture();
    }

    public Parent getRoot() {
        return root;
    }

    private MenuBar createMenu() {
        MenuItem newRecord = item("New Record", event -> clearForm());
        MenuItem refresh = item("Refresh Data", event -> refreshRecords());
        MenuItem upload = item("Upload Profile Picture", event -> chooseAndUploadPicture());
        upload.setDisable(true);
        MenuItem logout = item("Sign Out", event -> App.showAuth());
        MenuItem exit = item("Exit", event -> App.exit());
        Menu file = new Menu("File");
        file.getItems().addAll(newRecord, refresh, upload, new SeparatorMenuItem(), logout, exit);

        MenuItem delete = item("Delete Selected", event -> deleteSelected());
        MenuItem clear = item("Clear Form", event -> clearForm());
        Menu edit = new Menu("Edit");
        edit.getItems().addAll(delete, clear);

        MenuItem focusSearch = item("Focus Search", event -> searchField.requestFocus());
        MenuItem clearSearch = item("Clear Search", event -> searchField.clear());
        Menu view = new Menu("View");
        view.getItems().addAll(focusSearch, clearSearch);

        MenuItem about = item("About", event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Student Records Cloud uses Firebase Authentication, Cloud Firestore, and Firebase Storage.",
                    ButtonType.OK);
            alert.setTitle("About");
            alert.setHeaderText("Student Records Cloud");
            alert.showAndWait();
        });
        Menu help = new Menu("Help");
        help.getItems().add(about);

        MenuBar menu = new MenuBar(file, edit, view, help);
        menu.getStyleClass().add("main-menu");
        return menu;
    }

    private VBox createHeader() {
        Label title = new Label("Student Records Dashboard");
        title.getStyleClass().add("screen-title");
        Label subtitle = new Label("Signed in as " + session.email() + " · Records are stored in Cloud Firestore");
        subtitle.getStyleClass().add("screen-subtitle");

        searchField.getStyleClass().add("search-field");
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(event -> refreshRecords());
        HBox controls = new HBox(10, searchField, refresh);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        VBox header = new VBox(6, title, subtitle, controls);
        header.getStyleClass().add("page-header");
        return header;
    }

    private Pane createWorkspace() {
        VBox profileCard = createProfileCard();
        VBox recordForm = createRecordForm();
        VBox sidebar = new VBox(16, profileCard, recordForm);
        sidebar.setPrefWidth(280);

        table.getStyleClass().add("records-table");
        HBox workspace = new HBox(18, sidebar, table);
        workspace.setPadding(new Insets(18, 24, 18, 24));
        HBox.setHgrow(table, Priority.ALWAYS);
        return workspace;
    }

    private VBox createProfileCard() {
        profileImage.setFitWidth(112);
        profileImage.setFitHeight(112);
        profileImage.setPreserveRatio(true);
        profileImage.getStyleClass().add("profile-image");
        setPlaceholderImage();

        Label name = new Label(session.displayName());
        name.getStyleClass().add("card-title");
        Label email = new Label(session.email());
        email.getStyleClass().add("muted-label");
        email.setWrapText(true);

        Button upload = new Button("Upload Picture");
        upload.getStyleClass().add("secondary-button");
        upload.setMaxWidth(Double.MAX_VALUE);
        upload.setDisable(true);
        upload.setTooltip(new Tooltip("Firebase Storage requires billing and is not enabled."));

        VBox card = new VBox(8, profileImage, name, email, upload);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(18));
        card.getStyleClass().add("side-card");
        return card;
    }

    private VBox createRecordForm() {
        Label heading = new Label("New Student Record");
        heading.getStyleClass().add("card-title");

        saveButton = new Button("Save to Firestore");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setOnAction(event -> saveRecord());

        Button clear = new Button("Clear");
        clear.getStyleClass().add("secondary-button");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(event -> clearForm());

        VBox form = new VBox(8,
                heading,
                label("Name"), nameField,
                label("Major"), majorField,
                label("Age"), ageField,
                saveButton, clear);
        form.setPadding(new Insets(18));
        form.getStyleClass().add("side-card");
        return form;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(18, status, count);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No Firestore records found."));

        TableColumn<Person, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        name.setMinWidth(180);

        TableColumn<Person, String> major = new TableColumn<>("Major");
        major.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getMajor()));
        major.setMinWidth(170);

        TableColumn<Person, Number> age = new TableColumn<>("Age");
        age.setCellValueFactory(data -> new ReadOnlyIntegerWrapper(data.getValue().getAge()));
        age.setMinWidth(90);
        age.setMaxWidth(120);

        TableColumn<Person, String> id = new TableColumn<>("Firestore Document ID");
        id.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getId()));
        id.setMinWidth(250);

        table.getColumns().addAll(name, major, age, id);
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                status.setText("Selected " + selected.getName());
            }
        });
    }

    private void configureSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(person -> query.isBlank()
                    || person.getName().toLowerCase(Locale.ROOT).contains(query)
                    || person.getMajor().toLowerCase(Locale.ROOT).contains(query));
            updateCount();
        });
    }

    private void saveRecord() {
        String name = nameField.getText().trim();
        String major = majorField.getText().trim();
        int age;
        try {
            age = Integer.parseInt(ageField.getText().trim());
        } catch (NumberFormatException exception) {
            setStatus("Age must be a whole number.", false);
            return;
        }
        if (name.isBlank() || major.isBlank() || age < 1 || age > 125) {
            setStatus("Enter a name, major, and age from 1 to 125.", false);
            return;
        }

        saveButton.setDisable(true);
        setStatus("Saving record...", true);
        firebase.addPerson(new Person(name, major, age), session.uid()).whenComplete((saved, error) ->
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    if (error != null) {
                        setStatus(FirebaseService.rootMessage(error), false);
                        return;
                    }
                    records.add(0, saved);
                    table.getSelectionModel().select(saved);
                    clearForm();
                    setStatus("Record saved to Firestore.", true);
                    updateCount();
                }));
    }

    private void refreshRecords() {
        setStatus("Loading Firestore records...", true);
        firebase.loadPeople().whenComplete((people, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(FirebaseService.rootMessage(error), false);
                return;
            }
            records.setAll(people);
            updateCount();
            setStatus("Firestore records loaded.", true);
        }));
    }

    private void deleteSelected() {
        Person selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a record to delete.", false);
            return;
        }
        ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + selected.getName() + " from Firestore?", deleteButton, ButtonType.CANCEL);
        confirm.setHeaderText("Delete selected record");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != deleteButton) {
            return;
        }
        setStatus("Deleting record...", true);
        firebase.deletePerson(selected.getId()).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(FirebaseService.rootMessage(error), false);
                return;
            }
            records.remove(selected);
            updateCount();
            setStatus("Record deleted.", true);
        }));
    }

    private void chooseAndUploadPicture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a Profile Picture");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        profileImage.setImage(new Image(file.toURI().toString(), 112, 112, true, true));
        setStatus("Uploading profile picture...", true);
        firebase.uploadProfilePicture(file.toPath(), session.uid()).whenComplete((objectName, error) ->
                Platform.runLater(() -> {
                    if (error != null) {
                        setStatus(FirebaseService.rootMessage(error), false);
                    } else {
                        setStatus("Profile picture uploaded to Firebase Storage.", true);
                    }
                }));
    }

    private void loadProfilePicture() {
        firebase.loadProfilePicture(session.uid()).whenComplete((bytes, error) -> Platform.runLater(() -> {
            if (error == null && bytes != null && bytes.length > 0) {
                profileImage.setImage(new Image(new ByteArrayInputStream(bytes), 112, 112, true, true));
            }
        }));
    }

    private void clearForm() {
        nameField.clear();
        majorField.clear();
        ageField.clear();
        nameField.requestFocus();
    }

    private void updateCount() {
        int size = filtered.size();
        count.setText(size + (size == 1 ? " record" : " records"));
    }

    private void setStatus(String message, boolean success) {
        status.setText(message);
        status.getStyleClass().removeAll("status-success", "status-warning");
        status.getStyleClass().add(success ? "status-success" : "status-warning");
    }

    private void setPlaceholderImage() {
        var resource = getClass().getResource("/files/profile_empty.png");
        if (resource != null) {
            profileImage.setImage(new Image(resource.toExternalForm()));
        }
    }

    private static TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("form-field");
        return field;
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static MenuItem item(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(handler);
        return item;
    }
}
