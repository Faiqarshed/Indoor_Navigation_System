package com.google.ar.core.examples.java.cloudanchor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.common.graph.GraphBuilder;

public class GraphBuilderService1 extends Service {
    private GraphBuilder1 graphBuilder;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String currentLocation = intent.getStringExtra("CURRENT_LOCATION");
            String destinationLocation = intent.getStringExtra("DESTINATION_LOCATION");

            Log.d("A** graphbuilderservice", "Received: " + currentLocation + " → " + destinationLocation);
            graphBuilder = new GraphBuilder1(this);
            Log.e("Cunt", "Entered GraphBuilderService");
            graphBuilder.startandend(currentLocation, destinationLocation);
            graphBuilder.buildGraph();
        }
        return START_NOT_STICKY; // Ensures the service stops after execution
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
