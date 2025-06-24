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

public class MainActivity extends AppCompatActivity {

    String current_loc = "";
    AutoCompleteTextView searchInput;
    String[] destinations = {
            "Admission Office", "Ground Floor", "First Floor", "Second Floor",
            "Third Floor", "4th Floor", "HOD Office", "Dean Office"
    };

    //@Override
    @SuppressLint("MissingInflatedId")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen2);

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

        Button btnDone = findViewById(R.id.btnDone1);
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, Screen1Activity.class);
                intent.putExtra("current_location" , current_loc);
                System.out.println("Current location is : " + current_loc);
                Log.d("A**", "Current Location is " + current_loc);
                startActivity(intent);
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
                    current_loc = cloudAnchorId;
                    Log.d("Firebase1screen", "Cloud Anchor ID: " + cloudAnchorId);
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
