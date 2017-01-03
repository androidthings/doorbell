package com.example.androidthings.doorbell;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;

public class DoorbellApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
