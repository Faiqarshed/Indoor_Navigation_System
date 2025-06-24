package com.google.ar.core.examples.java.cloudanchor;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Screen1Activity extends AppCompatActivity {
    String destination_loc = "";
    AutoCompleteTextView searchInput;
    String[] destinations = {
            "Admission Office", "Ground Floor", "First Floor", "Second Floor",
            "Third Floor", "4th Floor", "HOD Office", "Dean Office"
    };

    //@Override
    @SuppressLint("MissingInflatedId")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen1);

        String current_location = getIntent().getStringExtra("current_location");


        searchInput = findViewById(R.id.searchInput);
        searchInput.setOnItemClickListener((adapterView, view, position, id) -> {
            String selectedText = adapterView.getItemAtPosition(position).toString();
            String formattedKey = selectedText.toLowerCase().replace(" ", "_");
            fetchCloudAnchorId(formattedKey); // call Firebase fetch
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                destinations
        );
        searchInput.setAdapter(adapter);
        searchInput.setThreshold(1); // Start suggesting after 1 character

        Button btnDone = findViewById(R.id.btnDone);
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serviceIntent = new Intent(v.getContext(), GraphBuilderService1.class);
                Log.e("Cunt", "Entering GraphBuilderService");
                serviceIntent.putExtra("CURRENT_LOCATION", current_location);
                serviceIntent.putExtra("DESTINATION_LOCATION", destination_loc);

                Log.e("Cunt", "Entering GraphActivity with: " +
                        current_location + " → " + destination_loc);

                startService(serviceIntent);
            }
        });

    }

    private void fetchCloudAnchorId(String key) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance()
                .getReference("detected_images")
                .child(key)
                .child("cloudAnchorId");

        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String cloudAnchorId = dataSnapshot.getValue(String.class);
                    destination_loc = cloudAnchorId;
                    Log.d("Firebase2screen", "Cloud Anchor ID: " + cloudAnchorId);
                    Log.d("A**", "Destination Location is " + destination_loc);

                    // Store or use the value as needed
                    // e.g. MainActivity.this.cloudAnchorId = cloudAnchorId;
                } else {
                    Log.d("Firebase", "No Cloud Anchor ID found for: " + key);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("Firebase", "Database error: " + databaseError.getMessage());

            }
        });
    }

}