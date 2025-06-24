package com.google.ar.core.examples.java.cloudanchor;

public class MyApplicationContextProvider extends android.app.Application {
    private static MyApplicationContextProvider instance;

    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static android.content.Context getContext() {
        return instance.getApplicationContext();
    }
}
