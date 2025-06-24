/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.cloudanchor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.Anchor;
import com.google.common.base.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A helper class to manage all communications with Firebase. */
class FirebaseManager {
  private static final String TAG =
          CloudAnchorActivity.class.getSimpleName() + "." + FirebaseManager.class.getSimpleName();

  /**
   * Listener for a new room code.
   */
  interface RoomCodeListener {

    /**
     * Invoked when a new room code is available from Firebase.
     */
    void onNewRoomCode(Long newRoomCode);

    /**
     * Invoked if a Firebase Database Error happened while fetching the room code.
     */
    void onError(DatabaseError error);
  }

  /**
   * Listener for a new cloud anchor ID.
   */
  interface CloudAnchorIdListener {

    /**
     * Invoked when a new cloud anchor ID is available.
     */
    void onNewCloudAnchorId(String cloudAnchorId);
  }

  // Names of the nodes used in the Firebase Database
  private static final String ROOT_FIREBASE_HOTSPOTS = "hotspot_list";
  private static final String ROOT_LAST_ROOM_CODE = "last_room_code";

  // Some common keys and values used when writing to the Firebase Database.
  private static final String KEY_DISPLAY_NAME = "display_name";
  private static final String KEY_ANCHOR_ID = "hosted_anchor_id";
  private static final String KEY_TIMESTAMP = "updated_at_timestamp";
  private static final String DISPLAY_NAME_VALUE = "Android EAP Sample";

  private final FirebaseApp app;
  private final DatabaseReference hotspotListRef;
  private final DatabaseReference roomCodeRef;
  private DatabaseReference currentRoomRef = null;
  private ValueEventListener currentRoomListener = null;


  /**
   * Default constructor for the FirebaseManager.
   *
   * @param context The application context.
   */
  FirebaseManager(Context context) {
    app = FirebaseApp.initializeApp(context);
    if (app != null) {
      DatabaseReference rootRef = FirebaseDatabase.getInstance(app).getReference();
      hotspotListRef = rootRef.child(ROOT_FIREBASE_HOTSPOTS);
      roomCodeRef = rootRef.child(ROOT_LAST_ROOM_CODE);

      DatabaseReference.goOnline();
    } else {
      Log.d(TAG, "Could not connect to Firebase Database!");
      hotspotListRef = null;
      roomCodeRef = null;
    }
  }

  /**
   * Gets a new room code from the Firebase Database. Invokes the listener method when a new room
   * code is available.
   */
  public void getNearbyAnchors(String newAnchorId, double x, double y, double z, double threshold, NearbyAnchorsCallback callback) {
    Log.d("NearbyAnchors", "🔹 Function called for Anchor ID: " + newAnchorId);

    DatabaseReference anchorsRef = FirebaseDatabase.getInstance().getReference("anchors");

    anchorsRef.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(@NonNull DataSnapshot snapshot) {
        Log.d("NearbyAnchors", "📥 Data retrieved from Firebase");

        List<String> nearbyAnchors = new ArrayList<>();

        if (snapshot.exists()) {
          for (DataSnapshot anchor : snapshot.getChildren()) {
            String anchorId = anchor.getKey();
            if (anchorId.equals(newAnchorId)) continue; // Skip itself

            try {
              double ax = anchor.child("x").getValue(Double.class);
              double ay = anchor.child("y").getValue(Double.class);
              double az = anchor.child("z").getValue(Double.class);

              Log.d("NearbyAnchors", "📌 Checking anchor: " + anchorId + " at (" + ax + ", " + ay + ", " + az + ")");

              if (calculateDistance(x, y, z, ax, ay, az) < threshold) {
                nearbyAnchors.add(anchorId);
                Log.d("NearbyAnchors", "✅ Nearby Anchor Found: " + anchorId);
              }
            } catch (Exception e) {
              Log.e("NearbyAnchors", "❌ Error parsing anchor data: " + e.getMessage());
            }
          }
        } else {
          Log.d("NearbyAnchors", "⚠️ No anchors found in Firebase.");
        }

        Log.d("NearbyAnchors", "📝 Nearby Anchors List: " + nearbyAnchors.toString());



        Log.e("LETS1", "NewAnchorId " + newAnchorId);
        // ✅ Store nearby anchors in Firebase
        updateAnchorConnections(newAnchorId, nearbyAnchors);

        // 🔹 Return list via callback
        callback.onNearbyAnchorsFound(nearbyAnchors);
      }

      @Override
      public void onCancelled(@NonNull DatabaseError error) {
        Log.e("FirebaseError", "❌ Firebase Query Cancelled: " + error.getMessage());
      }
    });
  }




  public double calculateDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
    return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
  }


  public void updateAnchorConnections(String newAnchorId, List<String> connectedAnchors) {
    DatabaseReference anchorRef = FirebaseDatabase.getInstance().getReference("anchorConnections");


    for (String anchorId : connectedAnchors) {
      // ✅ Store connections properly under each anchor
      anchorRef.child(newAnchorId).child(anchorId).setValue(true);
      anchorRef.child(anchorId).child(newAnchorId).setValue(true);
    }
  }


  public interface NearbyAnchorsCallback {
    void onNearbyAnchorsFound(List<String> nearbyAnchors);
  }

  void getNewRoomCode(RoomCodeListener listener) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    roomCodeRef.runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                Long nextCode = Long.valueOf(1);
                Object currVal = currentData.getValue();
                if (currVal != null) {
                  Long lastCode = Long.valueOf(currVal.toString());
                  nextCode = lastCode + 1;
                }
                currentData.setValue(nextCode);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!committed) {
                  listener.onError(error);
                  return;
                }
                Long roomCode = currentData.getValue(Long.class);
                listener.onNewRoomCode(roomCode);
              }
            });
  }

  /**
   * Stores the given anchor ID in the given room code.
   */
  void storeAnchorIdInRoom(Long roomCode, String anchorId, String cloudAnchorId, float x, float y, float z) {
    Preconditions.checkNotNull(app, "Firebase App was null");

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference ref = database.getReference("anchors").child(anchorId);


    // Store multiple anchor IDs in a list under the same room
    CloudAnchorActivity.AnchorData anchor1 = new CloudAnchorActivity.AnchorData(roomCode, cloudAnchorId, anchorId, x,y,z);
    ref.setValue(anchor1)
        .addOnSuccessListener(aVoid -> Log.d("Anchor info", "Anchor data stored successfully!"))
        .addOnFailureListener(e -> Log.e("Anchor info", "Failed to store Anchor data", e));
  }

  public void storeAnchorPosition(Long roomCode ,String cloudAnchorId, float x, float y, float z) {
    if (roomCode == null || cloudAnchorId == null) {
      Log.e(TAG, "Cannot store anchor position: missing room code or cloud anchor ID.");
      return;
    }

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference roomRef = database.getReference().child("rooms").child(String.valueOf(roomCode));
    Map<String, Object> anchorData = new HashMap<>();
    anchorData.put("cloudAnchorId", cloudAnchorId);
    anchorData.put("x", x);
    anchorData.put("y", y);
    anchorData.put("z", z);
    roomRef.child("anchors").child(cloudAnchorId).setValue(anchorData);
  }

  public void storeImageDataInFirebase(String imageId, String cloudAnchorId) {
    if (cloudAnchorId == null || cloudAnchorId.isEmpty()) {
      Log.e(TAG, "❌ Cloud Anchor ID is null, not saving to Firebase.");
      return;
    }

    DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference("detected_images");
    Map<String, Object> imageData = new HashMap<>();
    imageData.put("imageId", imageId);
    imageData.put("cloudAnchorId", cloudAnchorId);

    databaseRef.child(imageId).setValue(imageData)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Image data saved successfully!"))
            .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to save image data", e));
  }



  /**
   * Registers a new listener for the given room code. The listener is invoked whenever the data for
   * the room code is changed.
   */
  void registerNewListenerForRoom(Long roomCode, CloudAnchorIdListener listener) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    clearRoomListener();

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    currentRoomRef = database.getReference("anchors");  // Access the correct path

    currentRoomListener = new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        List<String> anchorIds = new ArrayList<>();

        // Loop through all stored anchors
        for (DataSnapshot anchorSnapshot : dataSnapshot.getChildren()) {
          Long storedRoomCode = anchorSnapshot.child("roomCode").getValue(Long.class);
          String cloudAnchorId = anchorSnapshot.child("cloudAnchorid").getValue(String.class);

          Log.d(TAG, "Checking Firebase Entry: " + anchorSnapshot.getKey() +
                  ", RoomCode: " + storedRoomCode +
                  ", CloudAnchorId: " + cloudAnchorId);

          // Match roomCode and fetch cloudAnchorId
          if (storedRoomCode != null && storedRoomCode.equals(roomCode) && cloudAnchorId != null) {
            anchorIds.add(cloudAnchorId);
          }
        }

        if (!anchorIds.isEmpty()) {
          for (String anchorId : anchorIds) {
            Log.d(TAG, "Fetched Cloud Anchor ID: " + anchorId);
            listener.onNewCloudAnchorId(anchorId);
          }
        } else {
          Log.e(TAG, "No Cloud Anchor found for room: " + roomCode);
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        Log.w(TAG, "The Firebase operation was cancelled.", databaseError.toException());
      }
    };

    currentRoomRef.addValueEventListener(currentRoomListener);
  }



  /**
   * Resets the current room listener registered using {@link #registerNewListenerForRoom(Long,
   * CloudAnchorIdListener)}.
   */
  void clearRoomListener() {
    if (currentRoomListener != null && currentRoomRef != null) {
      currentRoomRef.removeEventListener(currentRoomListener);
      currentRoomListener = null;
      currentRoomRef = null;
    }
  }

  public static class LandmarkData {
    public String id;


    public LandmarkData() {
    } // Required for Firebase

    public LandmarkData(String id) {
      this.id = id;
    }
  }
}
