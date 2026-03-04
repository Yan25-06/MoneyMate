package com.example.moneymate.data.repository;

import com.example.moneymate.data.remote.FirebaseAuthHelper;

/**
 * Repository handling authentication (Firebase Auth).
 */
public class AuthRepository {
    private final FirebaseAuthHelper firebaseAuthHelper;

    public AuthRepository(FirebaseAuthHelper firebaseAuthHelper) {
        this.firebaseAuthHelper = firebaseAuthHelper;
    }

    public FirebaseAuthHelper getFirebaseAuthHelper() {
        return firebaseAuthHelper;
    }

    public boolean isLoggedIn() {
        return firebaseAuthHelper.isLoggedIn();
    }

    public String getCurrentUserId() {
        return firebaseAuthHelper.getCurrentUserId();
    }

    public void signOut() {
        firebaseAuthHelper.signOut();
    }
}
