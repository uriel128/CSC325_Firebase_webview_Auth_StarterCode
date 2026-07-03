package com.example.csc325_firebase_webview_auth.model;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FirebaseService implements AutoCloseable {
    private static final FirebaseService INSTANCE = new FirebaseService();
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "firebase-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final Gson gson = new Gson();

    private volatile Firestore firestore;
    private volatile FirebaseAuth auth;
    private volatile String apiKey = "";
    private volatile String storageBucket = "";
    private volatile String projectId = "";
    private volatile String status = "Firebase is not initialized";

    private FirebaseService() {
    }

    public static FirebaseService getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<Boolean> initializeAsync() {
        return CompletableFuture.supplyAsync(this::initialize, executor);
    }

    private boolean initialize() {
        try {
            Properties properties = loadProperties();
            apiKey = firstNonBlank(System.getenv("FIREBASE_WEB_API_KEY"), properties.getProperty("apiKey"));
            storageBucket = firstNonBlank(System.getenv("FIREBASE_STORAGE_BUCKET"), properties.getProperty("storageBucket"));
            projectId = firstNonBlank(System.getenv("FIREBASE_PROJECT_ID"), properties.getProperty("projectId"));

            try (InputStream keyStream = openCredentials()) {
                if (keyStream == null) {
                    status = "Missing key.json. Copy the service-account file to the project root.";
                    return false;
                }

                GoogleCredentials credentials = GoogleCredentials.fromStream(keyStream);
                FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
                if (!projectId.isBlank()) {
                    options.setProjectId(projectId);
                }
                if (!storageBucket.isBlank()) {
                    options.setStorageBucket(storageBucket);
                }

                FirebaseApp app = FirebaseApp.getApps().isEmpty()
                        ? FirebaseApp.initializeApp(options.build())
                        : FirebaseApp.getInstance();
                firestore = FirestoreClient.getFirestore(app);
                auth = FirebaseAuth.getInstance(app);
                status = apiKey.isBlank()
                        ? "Cloud connected. Add apiKey to firebase.properties to enable password sign-in."
                        : storageBucket.isBlank()
                        ? "Firebase Auth and Firestore are connected. Storage is not configured."
                        : "Firebase Auth, Firestore, and Storage are connected.";
                return true;
            }
        } catch (Exception exception) {
            status = "Firebase connection failed: " + rootMessage(exception);
            return false;
        }
    }

    public String getStatus() {
        return status;
    }

    public boolean isCloudReady() {
        return firestore != null && auth != null;
    }

    public boolean isSignInConfigured() {
        return isCloudReady() && !apiKey.isBlank();
    }

    public CompletableFuture<UserSession> register(String email, String password, String displayName) {
        return supply(() -> {
            requireCloud();
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email.trim())
                    .setPassword(password)
                    .setDisplayName(displayName.trim())
                    .setEmailVerified(false)
                    .setDisabled(false);
            UserRecord user = auth.createUser(request);
            Map<String, Object> profile = new HashMap<>();
            profile.put("email", user.getEmail());
            profile.put("displayName", user.getDisplayName());
            profile.put("createdAt", System.currentTimeMillis());
            firestore.collection("profiles").document(user.getUid()).set(profile).get();
            return new UserSession(user.getUid(), user.getEmail(), user.getDisplayName(), "");
        });
    }

    public CompletableFuture<UserSession> signIn(String email, String password) {
        return supply(() -> {
            requireCloud();
            if (apiKey.isBlank()) {
                throw new IllegalStateException("Password sign-in requires apiKey in firebase.properties.");
            }

            JsonObject body = new JsonObject();
            body.addProperty("email", email.trim());
            body.addProperty("password", password);
            body.addProperty("returnSecureToken", true);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() / 100 != 2) {
                throw new IllegalArgumentException(readFirebaseError(json));
            }
            String uid = json.get("localId").getAsString();
            String signedInEmail = json.get("email").getAsString();
            String token = json.get("idToken").getAsString();
            UserRecord user = auth.getUser(uid);
            return new UserSession(uid, signedInEmail,
                    user.getDisplayName() == null ? signedInEmail : user.getDisplayName(), token);
        });
    }

    public CompletableFuture<Person> addPerson(Person person, String ownerUid) {
        return supply(() -> {
            requireCloud();
            String id = person.getId().isBlank() ? UUID.randomUUID().toString() : person.getId();
            Map<String, Object> data = new HashMap<>();
            data.put("Name", person.getName());
            data.put("Major", person.getMajor());
            data.put("Age", person.getAge());
            data.put("ownerUid", ownerUid);
            data.put("updatedAt", System.currentTimeMillis());
            firestore.collection("References").document(id).set(data).get();
            return new Person(id, person.getName(), person.getMajor(), person.getAge());
        });
    }

    public CompletableFuture<List<Person>> loadPeople() {
        return supply(() -> {
            requireCloud();
            List<QueryDocumentSnapshot> documents = firestore.collection("References").get().get().getDocuments();
            List<Person> people = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) {
                people.add(new Person(
                        document.getId(),
                        stringValue(document, "Name"),
                        stringValue(document, "Major"),
                        intValue(document, "Age")));
            }
            return people;
        });
    }

    public CompletableFuture<Void> deletePerson(String documentId) {
        return supply(() -> {
            requireCloud();
            firestore.collection("References").document(documentId).delete().get();
            return null;
        });
    }

    public CompletableFuture<String> uploadProfilePicture(Path file, String uid) {
        return supply(() -> {
            requireCloud();
            if (storageBucket.isBlank()) {
                throw new IllegalStateException("Set storageBucket in firebase.properties before uploading.");
            }
            String extension = extensionOf(file.getFileName().toString());
            String objectName = "profile-pictures/" + uid + "/profile" + extension;
            String contentType = Files.probeContentType(file);
            Bucket bucket = StorageClient.getInstance().bucket();
            bucket.create(objectName, Files.readAllBytes(file),
                    contentType == null ? "application/octet-stream" : contentType);
            Map<String, Object> update = Map.of(
                    "profilePicture", "gs://" + bucket.getName() + "/" + objectName,
                    "updatedAt", System.currentTimeMillis());
            firestore.collection("profiles").document(uid).set(update,
                    com.google.cloud.firestore.SetOptions.merge()).get();
            return objectName;
        });
    }

    public CompletableFuture<byte[]> loadProfilePicture(String uid) {
        return supply(() -> {
            requireCloud();
            if (storageBucket.isBlank()) {
                return null;
            }
            DocumentSnapshot profile = firestore.collection("profiles").document(uid).get().get();
            String uri = profile.getString("profilePicture");
            if (uri == null || uri.isBlank()) {
                return null;
            }
            String prefix = "gs://" + StorageClient.getInstance().bucket().getName() + "/";
            String objectName = uri.startsWith(prefix) ? uri.substring(prefix.length()) : uri;
            Blob blob = StorageClient.getInstance().bucket().get(objectName);
            return blob == null ? null : blob.getContent();
        });
    }

    private Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        Path external = Path.of("firebase.properties");
        if (Files.isRegularFile(external)) {
            try (InputStream input = Files.newInputStream(external)) {
                properties.load(input);
            }
            return properties;
        }
        try (InputStream input = getClass().getResourceAsStream("/files/firebase.properties")) {
            if (input != null) {
                properties.load(input);
            }
        }
        return properties;
    }

    private InputStream openCredentials() throws IOException {
        String configuredPath = firstNonBlank(
                System.getProperty("firebase.key.path"),
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
        if (!configuredPath.isBlank() && Files.isRegularFile(Path.of(configuredPath))) {
            return Files.newInputStream(Path.of(configuredPath));
        }
        Path rootKey = Path.of("key.json");
        if (Files.isRegularFile(rootKey)) {
            return Files.newInputStream(rootKey);
        }
        return getClass().getResourceAsStream("/files/key.json");
    }

    private void requireCloud() {
        if (!isCloudReady()) {
            throw new IllegalStateException(status);
        }
    }

    private <T> CompletableFuture<T> supply(ThrowingSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private static String stringValue(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value == null ? "" : value.toString();
    }

    private static int intValue(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static String readFirebaseError(JsonObject json) {
        try {
            String code = json.getAsJsonObject("error").get("message").getAsString();
            return switch (code) {
                case "EMAIL_NOT_FOUND", "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD" -> "Incorrect email or password.";
                case "USER_DISABLED" -> "This account is disabled.";
                case "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Try again later.";
                default -> code.replace('_', ' ').toLowerCase();
            };
        } catch (Exception ignored) {
            return "Firebase sign-in failed.";
        }
    }

    public static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? ".jpg" : fileName.substring(dot).toLowerCase();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !value.startsWith("YOUR_")) {
                return value.trim();
            }
        }
        return "";
    }

    @Override
    public void close() {
        executor.shutdownNow();
        if (firestore != null) {
            try {
                firestore.close();
            } catch (Exception ignored) {
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
