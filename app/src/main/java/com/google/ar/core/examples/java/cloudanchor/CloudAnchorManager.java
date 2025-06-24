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

import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Frame;
import com.google.ar.core.FutureState;
import com.google.ar.core.HostCloudAnchorFuture;
import com.google.ar.core.ResolveCloudAnchorFuture;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * A helper class to handle all the Cloud Anchors logic, and add a callback-like mechanism on top of
 * the existing ARCore API.
 */
class CloudAnchorManager {
  private static final String TAG =
      CloudAnchorActivity.class.getSimpleName() + "." + CloudAnchorManager.class.getSimpleName();
  private static final long DURATION_FOR_NO_RESOLVE_RESULT_MS = 10000;
  private long deadlineForMessageMillis;
  private int totalAnchorsToResolve = 0;
  private int resolvedAnchorsCount = 0;
  private boolean resolutionFinished = false;

  /** Listener for the results of a host operation. */
  interface CloudAnchorHostListener {

    /** This method is invoked when the results of a Cloud Anchor operation are available. */
    void onCloudTaskComplete(@Nullable String cloudAnchorId, CloudAnchorState cloudAnchorState);
  }

  /** Listener for the results of a resolve operation. */
  interface CloudAnchorResolveListener {

    /** This method is invoked when the results of a Cloud Anchor operation are available. */
    void onCloudTaskComplete(@Nullable Anchor anchor, CloudAnchorState cloudAnchorState);

    /** This method show the toast message. */
    void onShowResolveMessage();

    void onShowResolveMessage1();
  }

  @Nullable private Session session = null;

  /** The pending hosting operations. */
  private final ArrayList<Pair<HostCloudAnchorFuture, CloudAnchorHostListener>> hostTasks =
      new ArrayList<>();

  /** The pending resolving operations. */
  private final ArrayList<Pair<ResolveCloudAnchorFuture, CloudAnchorResolveListener>> resolveTasks =
      new ArrayList<>();

  /**
   * This method is used to set the session, since it might not be available when this object is
   * created.
   */
  synchronized void setSession(Session session) {
    this.session = session;
  }

  /**
   * This method hosts an anchor. The {@code listener} will be invoked when the results are
   * available.
   */
  synchronized void hostCloudAnchor(Anchor anchor, CloudAnchorHostListener listener) {
    Preconditions.checkNotNull(session, "The session cannot be null.");

    HostCloudAnchorFuture future = session.hostCloudAnchorAsync(anchor, 365, (cloudAnchorId, state) -> {
      if (state == CloudAnchorState.SUCCESS) {
        Log.d("CloudAnchor420", "✅ Hosting successful! Anchor ID: " + cloudAnchorId);
      } else {
        Log.e("CloudAnchor420", "❌ Hosting failed with state: " + state);
        switch (state) {
          case ERROR_INTERNAL:
            Log.e("CloudAnchor420", "🔴 Internal Error: Something went wrong with ARCore.");
            break;
          case ERROR_NOT_AUTHORIZED:
            Log.e("CloudAnchor420", "🔴 Not Authorized: Check ARCore API Key in Google Cloud.");
            break;
          case ERROR_RESOURCE_EXHAUSTED:
            Log.e("CloudAnchor420", "🔴 Resource Exhausted: You exceeded the quota for hosting.");
            break;
          case ERROR_SERVICE_UNAVAILABLE:
            Log.e("CloudAnchor420", "🔴 Service Unavailable: ARCore Cloud Anchors might be down.");
            break;
          case ERROR_HOSTING_DATASET_PROCESSING_FAILED:
            Log.e("CloudAnchor420", "🔴 Dataset Processing Failed: ARCore couldn’t extract visual features.");
            break;
          case ERROR_CLOUD_ID_NOT_FOUND:
            Log.e("CloudAnchor420", "🔴 Cloud ID Not Found: The anchor ID doesn’t exist.");
            break;
          default:
            Log.e("CloudAnchor420", "🔴 Unknown error occurred.");
            break;
        }
      }
    });

    hostTasks.add(new Pair<>(future, listener));
  }




  /**
   * This method resolves an anchor. The {@code listener} will be invoked when the results are
   * available.
   */
  synchronized void resolveCloudAnchor(
      String anchorId, CloudAnchorResolveListener listener, long startTimeMillis) {
    Preconditions.checkNotNull(session, "The session cannot be null.");
    ResolveCloudAnchorFuture future = session.resolveCloudAnchorAsync(anchorId, null);
    resolveTasks.add(new Pair<>(future, listener));
    deadlineForMessageMillis = startTimeMillis + DURATION_FOR_NO_RESOLVE_RESULT_MS;
  }


  private boolean resolveTimerStarted = false;
  private long resolveStartTime = 0;

  /** Should be called after a {@link Session#update()} call. */
  synchronized void onUpdate() throws CameraNotAvailableException {
    Preconditions.checkNotNull(session, "The session cannot be null.");
    Iterator<Pair<HostCloudAnchorFuture, CloudAnchorHostListener>> hostIter = hostTasks.iterator();
    while (hostIter.hasNext()) {
      Pair<HostCloudAnchorFuture, CloudAnchorHostListener> entry = hostIter.next();
      if (entry.first.getState() == FutureState.DONE) {
        CloudAnchorHostListener listener = entry.second;
        String cloudAnchorId = entry.first.getResultCloudAnchorId();
        CloudAnchorState cloudAnchorState = entry.first.getResultCloudAnchorState();
        listener.onCloudTaskComplete(cloudAnchorId, cloudAnchorState);
        hostIter.remove();
      }
    }

   // Frame frame = session.update();
    // Process augmented images
    //processAugmentedImages(frame);

    Iterator<Pair<ResolveCloudAnchorFuture, CloudAnchorResolveListener>> resolveIter =
        resolveTasks.iterator();
    Log.d("Checkifin", "Number of pending resolve tasks: " + resolveTasks.size());

    // Start resolve timer only once
    if (!resolveTimerStarted && !resolveTasks.isEmpty()) {
      resolveStartTime = SystemClock.uptimeMillis();
      resolveTimerStarted = true;
      Log.d("Checkifin", "⏱️ Started 5-second timeout for resolve check");
    }

    while (resolveIter.hasNext()) {
      Pair<ResolveCloudAnchorFuture, CloudAnchorResolveListener> entry = resolveIter.next();
      CloudAnchorResolveListener listener = entry.second;
      Log.d("Checkifin", "Checking resolve state: " + entry.first.getState());
      if (entry.first.getState() == FutureState.DONE) {
        Anchor anchor = entry.first.getResultAnchor();
        CloudAnchorState cloudAnchorState = entry.first.getResultCloudAnchorState();
        Log.d("Checkifin", "Resolved Anchor state: " + cloudAnchorState);

        if (cloudAnchorState == CloudAnchorState.SUCCESS) {
          resolvedAnchorsCount++; // ✅ Count successful ones
        }

        Log.d("Checkifin", "✅ Resolved Anchors: " + resolvedAnchorsCount);

        listener.onCloudTaskComplete(anchor, cloudAnchorState);
        resolveIter.remove();

          if (resolvedAnchorsCount == 0) {
            listener.onShowResolveMessage1();  // ✅ This will trigger "You are at the wrong location"
          }

      }
      if (deadlineForMessageMillis > 0 && SystemClock.uptimeMillis() > deadlineForMessageMillis) {
        listener.onShowResolveMessage();
        deadlineForMessageMillis = 0;
      }

      // ⏰ After 5 seconds, check if still 0 resolved
      if (resolveTimerStarted && SystemClock.uptimeMillis() - resolveStartTime > 5000) {
        if (resolvedAnchorsCount == 0) {
          Log.d("Checkifin", "⚠️ No anchors resolved after 5 seconds");
          for (Pair<ResolveCloudAnchorFuture, CloudAnchorResolveListener> entry1 : resolveTasks) {
            entry.second.onShowResolveMessage1();  // Show: You're at the wrong location
          }
          resolveTimerStarted = false; // prevent it from repeating
        }
      }
    }
  }

  private void processAugmentedImages(Frame frame) {
    Collection<AugmentedImage> updatedAugmentedImages =
            frame.getUpdatedTrackables(AugmentedImage.class);

    for (AugmentedImage image : updatedAugmentedImages) {
      if (image.getTrackingState() == TrackingState.TRACKING) {
        Log.d("ARCore", "Recognized Image: " + image.getName());

        // Store to Firebase if needed
        saveToFirebase(image.getName());
      }
    }
  }

  public void resolveAStarPath(List<String> anchorIds, CloudAnchorResolveListener listener) {
    Preconditions.checkNotNull(session, "The session cannot be null.");

    totalAnchorsToResolve = anchorIds.size();   // ✅ how many to resolve
    Log.d("Checkifin", "Anchors to resolve: " + anchorIds.size());
    resolvedAnchorsCount = 0;
    resolutionFinished = false;

    for (String anchorId : anchorIds) {
      ResolveCloudAnchorFuture future = session.resolveCloudAnchorAsync(anchorId, null);
      resolveTasks.add(new Pair<>(future, listener));
    }
  }


  private void saveToFirebase(String landmarkName) {
    // Get Firebase Database reference
    DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference("landmarks");

    // Generate a unique ID for each recognized landmark
    String id = databaseRef.push().getKey();

    // Store landmark with its ID
    if (id != null) {
      databaseRef.child(id).setValue(landmarkName)
              .addOnSuccessListener(aVoid -> Log.d("Firebase", "Landmark saved: " + landmarkName))
              .addOnFailureListener(e -> Log.e("Firebase", "Failed to save landmark", e));
    }
  }


  /** Used to clear any currently registered listeners, so they won't be called again. */
  synchronized void clearListeners() {
    hostTasks.clear();
    resolveTasks.clear();
    deadlineForMessageMillis = 0;
  }


}
