package com.example.csc325_firebase_webview_auth.model;

public record UserSession(String uid, String email, String displayName, String idToken) {
}
