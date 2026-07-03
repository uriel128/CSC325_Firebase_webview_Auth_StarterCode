# Student Records Cloud

JavaFX desktop application using Firebase Authentication and Cloud Firestore.

## Features

- Splash screen during Firebase initialization
- Registration and email/password sign-in
- Working File, Edit, View, and Help menus
- Firestore create, read, and delete operations
- Searchable TableView output
- CSS design carried forward from the Menu/TableView assignment

Firebase Storage is intentionally not enabled because new Firebase Storage projects require a billing account. The instructor should approve this exception before submission.

## Local configuration

The ignored files `key.json` and `firebase.properties` must be present in the project root.
Run the project with `./mvnw javafx:run`.
