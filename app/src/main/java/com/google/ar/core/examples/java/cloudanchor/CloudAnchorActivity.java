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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.ar.core.AugmentedImage;

import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.GuardedBy;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.cloudanchor.PrivacyNoticeDialogFragment.HostResolveListener;
import com.google.ar.core.examples.java.cloudanchor.PrivacyNoticeDialogFragment.NoticeDialogListener;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;


public class CloudAnchorActivity extends AppCompatActivity
    implements GLSurfaceView.Renderer, NoticeDialogListener {
  private static final String TAG = CloudAnchorActivity.class.getSimpleName();
  private static final float[] OBJECT_COLOR = new float[] {139.0f, 195.0f, 74.0f, 255.0f};
  private GraphBuilder1 graphBuilder;
  public String current_location = "";
  public String destination_loc = "";





  private enum HostResolveMode {
    NONE,
    HOSTING,
    RESOLVING,
  }

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;
  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final ObjectRenderer virtualObject = new ObjectRenderer();
  private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
  private final PlaneRenderer planeRenderer = new PlaneRenderer();
  private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
  private final HashMap<Integer, Anchor> imageAnchors = new HashMap<>();
  private String lastPlacedCloudAnchorId = null;
  private List<Anchor> storedAnchors = new ArrayList<>();
  private final Map<String, float[]> landmarkPositions = new HashMap<>();
  private float[] pendingAnchorPosition = null;
  private boolean shouldCreateAnchor = false;
  private boolean installRequested;

  // Temporary matrices allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];

  // Locks needed for synchronization
  private final Object singleTapLock = new Object();
  private final Object anchorLock = new Object();

  // Tap handling and UI.
  private GestureDetector gestureDetector;
  private final SnackbarHelper snackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private Button hostButton;
  private Button resolveButton;
  private TextView roomCodeText;
  private SharedPreferences sharedPreferences;
  private static final String PREFERENCE_FILE_KEY = "allow_sharing_images";
  private static final String ALLOW_SHARE_IMAGES_KEY = "ALLOW_SHARE_IMAGES";
  public static List<String> path;
  private List<Anchor> anchorList = new ArrayList<>(); // Store anchors



  @GuardedBy("singleTapLock")
  private MotionEvent queuedSingleTap;
  private Session session;
  @GuardedBy("anchorLock")
  private Anchor anchor;
  // Cloud Anchor Components.
  private FirebaseManager firebaseManager;
  private final CloudAnchorManager cloudManager = new CloudAnchorManager();
  private HostResolveMode currentMode;
  private RoomCodeAndCloudAnchorIdListener hostListener;
  private final List<Anchor> hostedAnchors = new ArrayList<>();
  public float x,y,z;
  private String lastPlacedAnchorId = null;
  Map<String, List<String>> connections = new HashMap<>();
  private static final int PERMISSION_REQUEST_CODE = 100; // Any integer value will work
  private final List<Anchor> resolvedAnchors = new ArrayList<>(); // 🔹 Fix: Declare the list
  private GoogleSignInClient googleSignInClient;






  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    current_location = getIntent().getStringExtra("current_location");
    destination_loc = getIntent().getStringExtra("destination_location");

    Log.d("A**", "Received curr: " + current_location + ", dest: " + destination_loc);

    System.out.println("A**" + current_location);
    System.out.println("A**" + destination_loc);


    try {
      System.loadLibrary("filament");
      System.loadLibrary("filament-jni");
      // Add other required Filament libraries
      Log.e("Native", "✅ Filament libraries loaded successfully");
    } catch (UnsatisfiedLinkError e) {
      Log.e("Native", "❌ Failed to load Filament libraries: " + e.getMessage());
    }

    try {
      InputStream inputStream = getResources().openRawResource(R.raw.oauth_client);
      GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
              JacksonFactory.getDefaultInstance(), new InputStreamReader(inputStream)
      );
    } catch (IOException e) {
      e.printStackTrace();
    }



    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("943189869832-3omccgp6sqss2gmsiihtaveg67575hfv.apps.googleusercontent.com")
            .requestEmail()
            .build();

    googleSignInClient = GoogleSignIn.getClient(this, gso);

    // Attempt automatic sign-in
    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
    if (account == null) {
      signIn(); // Trigger sign-in again if not signed in
    } else {
    }

    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(this);


    new android.app.AlertDialog.Builder(this)
            .setTitle("Location Confirmation")
            .setMessage("Are you at the correct location?")
            .setCancelable(false)
            .setPositiveButton("Yes", (dialog, which) -> {
              dialog.dismiss();
              // Start resolving anchors directly
              onResolveButtonPress();
              //resolveButton.setVisibility(View.GONE);
            })
            .setNegativeButton("No", (dialog, which) -> {
              dialog.dismiss();
              Toast.makeText(CloudAnchorActivity.this, "Please go to the correct location.", Toast.LENGTH_LONG).show();

              // Go back to GraphActivity
              Intent intent = new Intent(CloudAnchorActivity.this, MainActivity.class);
              intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
              finish(); // End ARCore activity
            })
            .show();





    // Set up touch listener.
    gestureDetector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                synchronized (singleTapLock) {
                  if (currentMode == HostResolveMode.HOSTING) {
                    queuedSingleTap = e;
                  }
                }
                return true;
              }

              @Override
              public boolean onDown(MotionEvent e) {
                return true;
              }
            });
    surfaceView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    surfaceView.setWillNotDraw(false);
    installRequested = false;

    // Initialize UI components.
    hostButton = findViewById(R.id.host_button);
    hostButton.setOnClickListener((view) -> onHostButtonPress());
    resolveButton = findViewById(R.id.resolve_button);
    resolveButton.setOnClickListener((view) -> onResolveButtonPress());
    roomCodeText = findViewById(R.id.room_code_text);

    // Initialize Cloud Anchor variables.
    firebaseManager = new FirebaseManager(this);
    currentMode = HostResolveMode.NONE;
    sharedPreferences = getSharedPreferences(PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);

    // Get the root layout of your activity
    FrameLayout rootLayout = findViewById(android.R.id.content);

    // Create the "Done" button
    Button btnDone = new Button(this);
    btnDone.setText("Done");
    btnDone.setBackgroundColor(Color.BLUE);
    btnDone.setTextColor(Color.WHITE);

    // Set button layout parameters to position at bottom-right
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    );
    params.gravity = Gravity.BOTTOM | Gravity.END; // Bottom-Right corner
    params.bottomMargin = 50; // Margin from bottom
    params.rightMargin = 50;  // Margin from right

    btnDone.setLayoutParams(params);

    // Add button to the existing root layout
    rootLayout.addView(btnDone);

    // Initialize GraphBuilder1
    graphBuilder = new GraphBuilder1(this);

    // Set click listener for button
    btnDone.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {

        Intent serviceIntent = new Intent(v.getContext(), GraphBuilderService1.class);
        serviceIntent.putExtra("CURRENT_LOCATION", current_location);
        serviceIntent.putExtra("DESTINATION_LOCATION", destination_loc);

        startService(serviceIntent);

        // Show a toast message to indicate the process has started
        Toast.makeText(CloudAnchorActivity.this,
                "Working...",
                Toast.LENGTH_SHORT).show();
      }
    });


  }





  @Override
  protected void onDestroy() {
    // Clear all registered listeners.
    resetMode();

    if (session != null) {
      // Explicitly close ARCore Session to release native resources.
      // Review the API reference for important considerations before calling close() in apps with
      // more complicated lifecycle requirements:
      // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
      Log.e("SessionCheck", "Session is Destroying");
      session.close();
      session = null;
    }

    super.onDestroy();
  }




  @Override
  protected void onResume() {
    super.onResume();

    if (sharedPreferences.getBoolean(ALLOW_SHARE_IMAGES_KEY, false)) {
      createSession();
    }
    snackbarHelper.showMessage(this, getString(R.string.snackbar_initial_message));
    surfaceView.onResume();
    displayRotationHelper.onResume();

  }




  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }





  // Handle sign-in result
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == 100) {
      Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
      try {
        GoogleSignInAccount account = task.getResult(ApiException.class);
      } catch (ApiException e) {
      }
    }
  }




  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
              .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }



  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }








  private void showInputDialog1(AugmentedImage augmentedImage, String cloudAnchorId) {
    ((Activity) CloudAnchorActivity.this).runOnUiThread(() -> {
      AlertDialog.Builder builder = new AlertDialog.Builder(CloudAnchorActivity.this);
      builder.setTitle("Enter Image ID");

      // Input Field
      final EditText input = new EditText(CloudAnchorActivity.this);
      input.setInputType(InputType.TYPE_CLASS_TEXT);
      builder.setView(input);

      // When User Clicks "OK"
      builder.setPositiveButton("OK", (dialog, which) -> {
        String imageId = input.getText().toString().trim();
        if (!imageId.isEmpty()) {
          firebaseManager.storeImageDataInFirebase(imageId, cloudAnchorId);
        }
      });

      builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

      builder.show();
    });
  }





  private void signIn() {
    Intent signInIntent = googleSignInClient.getSignInIntent();
    startActivityForResult(signInIntent, 100);
    Log.d("CloudAnchor420", "🔵 Triggered Google Sign-In Intent");
  }



//ARCORE SESSION AND RENDERING

  private void createSession() {
    if (session == null) {
      Exception exception = null;
      int messageId = -1;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }
        session = new Session(this);
      } catch (UnavailableArcoreNotInstalledException e) {
        messageId = R.string.snackbar_arcore_unavailable;
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        messageId = R.string.snackbar_arcore_too_old;
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        messageId = R.string.snackbar_arcore_sdk_too_old;
        exception = e;
      } catch (Exception e) {
        messageId = R.string.snackbar_arcore_exception;
        exception = e;
      }

      if (exception != null) {
        snackbarHelper.showError(this, getString(messageId));
        Log.e(TAG, "Exception creating session", exception);
        return;
      }

      // Create default config and check if supported.
      Config config = new Config(session);
      config.setFocusMode(Config.FocusMode.AUTO); // Enable Auto-focus
      config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED); // Keep Cloud Anchors enabled

      // Load Augmented Image Database
      config.setAugmentedImageDatabase(loadAugmentedImageDatabase(session));

      session.configure(config);
      Log.e("SessionCheck", "Session has been Configured");


      // Setting the session in the HostManager.
      cloudManager.setSession(session);
    }

    try {
      Log.e("SessionCheck", "Before resume, session = " + session);
      session.resume();
      Log.e("SessionCheck", "Session has been resumed");
    } catch (CameraNotAvailableException e) {
      snackbarHelper.showError(this, getString(R.string.snackbar_camera_unavailable));
      Log.e("SessionCheck", "Session has been set to null");
      session = null;
      return;
    }
  }





  private AugmentedImageDatabase loadAugmentedImageDatabase(Session session) {
    try (InputStream is = getResources().openRawResource(R.raw.augmented_image_database)) {
      return AugmentedImageDatabase.deserialize(session, is);
    } catch (IOException e) {
      Log.e(TAG, "Could not load augmented image database", e);
    }
    return new AugmentedImageDatabase(session);
  }






  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(this);
      planeRenderer.createOnGlThread(this, "models/trigrid.png");
      pointCloudRenderer.createOnGlThread(this);

      virtualObject.createOnGlThread(this, "models/andy.obj", "models/andy.png");
      virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      virtualObjectShadow.createOnGlThread(
              this, "models/andy_shadow.obj", "models/andy_shadow.png");
      virtualObjectShadow.setBlendMode(BlendMode.Shadow);
      virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);
    } catch (IOException ex) {
      Log.e(TAG, "Failed to read an asset file", ex);
    }
  }




  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }




  @Override
  public void onDrawFrame(GL10 gl) {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());
      Frame frame = session.update();
      Camera camera = frame.getCamera();
      TrackingState cameraTrackingState = camera.getTrackingState();

      // Process any pending anchor placement requests
      if (shouldCreateAnchor && pendingAnchorPosition != null &&
              camera.getTrackingState() == TrackingState.TRACKING) {

        float x = pendingAnchorPosition[0];
        float y = pendingAnchorPosition[1];
        float z = pendingAnchorPosition[2];

        // Create Pose for the anchor
        Pose anchorPose = new Pose(
                new float[]{x, y, z},
                new float[]{0, 0, 0, 1} // Default orientation (no rotation)
        );

        try {
          Anchor newAnchor = session.createAnchor(anchorPose);
          Log.i("AnchorPlacement", "Successfully created anchor at position: " +
                  Arrays.toString(anchorPose.getTranslation()));

          // Place the anchor in your scene
          placeAnchorInScene(newAnchor);
        } catch (Exception e) {
          Log.e("AnchorPlacement", "Error creating anchor: ", e);
        }

        // Reset flags
        shouldCreateAnchor = false;
        pendingAnchorPosition = null;
      }

      cloudManager.onUpdate();
      handleTap(frame, cameraTrackingState);
      backgroundRenderer.draw(frame);
      trackingStateHelper.updateKeepScreenOnFlag(cameraTrackingState);

      if (cameraTrackingState == TrackingState.PAUSED) {
        return;
      }

      camera.getViewMatrix(viewMatrix, 0);
      camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

      try (PointCloud pointCloud = frame.acquirePointCloud()) {
        pointCloudRenderer.update(pointCloud);
        pointCloudRenderer.draw(viewMatrix, projectionMatrix);
      }

      planeRenderer.drawPlanes(
              session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix);

      // 🔹 Augmented Image Detection Logic
      Collection<AugmentedImage> updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);

      for (AugmentedImage augmentedImage : updatedAugmentedImages) {
        if (augmentedImage.getTrackingState() == TrackingState.TRACKING) {
          if (!imageAnchors.containsKey(augmentedImage.getIndex())) {
            Anchor anchor = augmentedImage.createAnchor(
                    augmentedImage.getCenterPose().compose(Pose.makeTranslation(0, 0.05f, 0))
            );
            imageAnchors.put(augmentedImage.getIndex(), anchor);
            Log.d(TAG, "✅ Anchor placed at: " + anchor.getPose().toString());
            showInputDialog1(augmentedImage, lastPlacedCloudAnchorId);

            // ✅ Assign the last placed Cloud Anchor ID when an image is detected
            if (lastPlacedCloudAnchorId != null) {
              Log.d(TAG, "🔹 Assigning Cloud Anchor ID: " + lastPlacedCloudAnchorId + " to Augmented Image.");
            } else {
              Log.d(TAG, "⚠️ No Cloud Anchor ID available yet.");
            }
          }

          Anchor anchor = imageAnchors.get(augmentedImage.getIndex());

          if (anchor.getTrackingState() == TrackingState.TRACKING) {
            Log.d(TAG, "✅ Rendering at anchor position: " + anchor.getPose().toString());

            float[] anchorMatrix = new float[16];
            anchor.getPose().toMatrix(anchorMatrix, 0);

            // ✅ Make sure colorCorrectionRgba is properly set
            float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            float scaleFactor = 0.2f;  // Adjust size if needed
            virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, OBJECT_COLOR);
          } else {
            Log.d(TAG, "❌ Anchor is not tracking.");
          }
        }
      }

      synchronized (anchorLock) {
        for (Anchor anchor : hostedAnchors) {
          if (anchor.getTrackingState() == TrackingState.TRACKING) {
            float[] anchorMatrix = new float[16];
            anchor.getPose().toMatrix(anchorMatrix, 0);

            float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            float scaleFactor = 1.0f;
            virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, OBJECT_COLOR);
            virtualObjectShadow.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, OBJECT_COLOR);
          }
        }
      }

      synchronized (anchorLock) {
        // Render **RESOLVED** anchors from anchorList
        for (Anchor anchor : anchorList) {
          if (anchor.getTrackingState() == TrackingState.TRACKING) {
            float[] anchorMatrix = new float[16];
            anchor.getPose().toMatrix(anchorMatrix, 0);

            float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            if (anchor.getTrackingState() == TrackingState.TRACKING) {
              Log.d("anchorTracking", "Drawing anchor at: " + anchor.getPose().toString());
              OpenGLRenderer.drawAnchorAtPosition(anchorMatrix, viewMatrix, projectionMatrix);
            }

            float scaleFactor = 1.0f;
            virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, OBJECT_COLOR);
            virtualObjectShadow.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, OBJECT_COLOR);
          }
        }
      }

    } catch (Throwable t) {
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }



//ANCHOR MANAGEMENT

  /**
   * Handles the most recent user tap.
   *
   * <p>We only ever handle one tap at a time, since this app only allows for a single anchor.
   *
   * @param frame the current AR frame
   * @param cameraTrackingState the current camera tracking state
   */
  private void handleTap(Frame frame, TrackingState cameraTrackingState) {
    synchronized (singleTapLock) {
      synchronized (anchorLock) {
        if (queuedSingleTap != null && cameraTrackingState == TrackingState.TRACKING) {
          Preconditions.checkState(
                  currentMode == HostResolveMode.HOSTING,
                  "We should only be creating anchors in hosting mode.");

          for (HitResult hit : frame.hitTest(queuedSingleTap)) {
            if (shouldCreateAnchorWithHit(hit)) {
              Anchor newAnchor = hit.createAnchor();
              hostedAnchors.add(newAnchor);  // Store new anchor properly
              cloudManager.hostCloudAnchor(newAnchor, hostListener);
              snackbarHelper.showMessage(this, getString(R.string.snackbar_anchor_placed));

              // Extract X, Y, Z coordinates
              Pose anchorPose = newAnchor.getPose();
              x = anchorPose.tx();
              y = anchorPose.ty();
              z = anchorPose.tz();

            }
          }
        }
      }
      queuedSingleTap = null;
    }
  }


  /** Returns {@code true} if and only if the hit can be used to create an Anchor reliably. */
  private static boolean shouldCreateAnchorWithHit(HitResult hit) {
    Trackable trackable = hit.getTrackable();
    if (trackable instanceof Plane) {
      // Check if the hit was within the plane's polygon.
      return ((Plane) trackable).isPoseInPolygon(hit.getHitPose());
    } else if (trackable instanceof Point) {
      // Check if the hit was against an oriented point.
      return ((Point) trackable).getOrientationMode() == OrientationMode.ESTIMATED_SURFACE_NORMAL;
    }
    return false;
  }









  /** Sets the new value of the current anchor. Detaches the old anchor, if it was non-null. */
  private void setNewAnchor(Anchor newAnchor) {
    synchronized (anchorLock) {
      if (newAnchor != null) {
        hostedAnchors.add(newAnchor);  // Store all anchors instead of replacing
      }
    }
  }





  private void placeAnchorFromStoredPosition(float x, float y, float z) {
    if (session == null) {
      Log.e("AnchorPlacement", "ARCore session is null. Cannot place anchor.");
      return;
    }

    // Store the position for use in the next render frame
    pendingAnchorPosition = new float[]{x, y, z};
    shouldCreateAnchor = true;

    Log.i("AnchorPlacement", "Anchor position queued for placement at position (" +
            x + ", " + y + ", " + z + ") in next render frame");

    // Force a redraw to ensure our anchor gets created promptly
    if (surfaceView != null) {
      surfaceView.requestRender();
    }
  }



  private void placeAnchorInScene(Anchor anchor) {
    if (anchor == null) {
      Log.e("anchorList", "Attempted to add a null anchor!");
      return;
    }

    // Check if the anchor already exists in the list
    for (Anchor existingAnchor : anchorList) {
      if (existingAnchor.getPose().equals(anchor.getPose())) {
        Log.w("anchorList", "Duplicate anchor detected, skipping...");
        return;
      }
    }

    // If unique, add to the list
    anchorList.add(anchor);
    Log.e("anchorList", "Anchor placed in the scene. Total anchors: " + anchorList.size());
  }




  private void storeAnchor(Anchor anchor) {
    storedAnchors.add(anchor);
  }



  /** Resets the mode of the app to its initial state and removes the anchors. */
  private void resetMode() {
    hostButton.setText(R.string.host_button_text);
    hostButton.setEnabled(true);
    resolveButton.setText(R.string.resolve_button_text);
    resolveButton.setEnabled(true);
    roomCodeText.setText(R.string.initial_room_code);
    currentMode = HostResolveMode.NONE;
    firebaseManager.clearRoomListener();
    hostListener = null;
    setNewAnchor(null);
    snackbarHelper.hide(this);
    cloudManager.clearListeners();
  }



  //Cloud Anchor Hosting/Resolving



  /** Callback function invoked when the Host Button is pressed. */
  private void onHostButtonPress() {
    if (currentMode == HostResolveMode.HOSTING) {
      resetMode();
      return;
    }

    if (!sharedPreferences.getBoolean(ALLOW_SHARE_IMAGES_KEY, false)) {
      showNoticeDialog(this::onPrivacyAcceptedForHost);
    } else {
      onPrivacyAcceptedForHost();
    }
  }



  /** Callback function invoked when the Resolve Button is pressed. */
  private void onResolveButtonPress() {
    if (currentMode == HostResolveMode.RESOLVING) {
      resetMode();
      return;
    }

    if (session == null) {
      Log.e(TAG, "❌ Session is null in onResolveButtonPress — creating session");
      createSession();
      if (session == null) {
        Log.e(TAG, "❌ Still null after createSession. Abort resolving.");
        return; // Prevent crash if session couldn't be created
      }
    }

    resolveAnchorsFromPath();
  }



  private void onPrivacyAcceptedForHost() {
    if (hostListener != null) {
      return;
    }
    resolveButton.setEnabled(false);
    hostButton.setText(R.string.cancel);
    snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_host));

    hostListener = new RoomCodeAndCloudAnchorIdListener();
    firebaseManager.getNewRoomCode(hostListener);
  }



  private void onPrivacyAcceptedForResolve() {
    ResolveDialogFragment dialogFragment = new ResolveDialogFragment();
    dialogFragment.setOkListener(this::onRoomCodeEntered);
    dialogFragment.show(getSupportFragmentManager(), "ResolveDialog");
  }



  private boolean isResolving = false; // Prevent duplicate resolutions

  private void resolveAnchorsFromPath() {
    if (isResolving) {
      Log.d("New1", "resolveAnchorsFromPath: Already resolving, skipping duplicate call.");
      return;
    }
    isResolving = true;

    Log.d("New1", "resolveAnchorsFromPath: Starting to resolve anchors...");

    currentMode = HostResolveMode.RESOLVING;
    hostButton.setEnabled(false);
    resolveButton.setText(R.string.cancel);
    snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_resolve));

    Log.d("New2", "The path is " + path);


    for (String cloudAnchorId : path) {
      Log.d("New1", "resolveAnchorsFromPath: Resolving Cloud Anchor ID: " + cloudAnchorId);

      CloudAnchorResolveStateListener resolveListener = new CloudAnchorResolveStateListener();
      cloudManager.resolveCloudAnchor(cloudAnchorId, resolveListener, SystemClock.uptimeMillis());
    }
  }




  /*// Fetch & place anchors based on a list of Cloud Anchor IDs
  private void fetchAndPlaceAnchorsByCloudIds(List<String> cloudAnchorIdsList) {
    DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference("anchors");
    if (cloudAnchorIdsList == null || cloudAnchorIdsList.isEmpty()) {
      Log.e("Firebase", "No Cloud Anchor IDs provided.");
      return;
    }

    databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (!snapshot.exists()) {
          Log.e("Firebase", "No anchors found in database.");
          return;
        }

        for (DataSnapshot anchorSnapshot : snapshot.getChildren()) {
          String cloudAnchorId = anchorSnapshot.child("cloudAnchorid").getValue(String.class);

          // If this anchor's cloudAnchorId matches one in the provided list
          if (cloudAnchorIdsList.contains(cloudAnchorId)) {
            double x = anchorSnapshot.child("x").getValue(Double.class);
            double y = anchorSnapshot.child("y").getValue(Double.class);
            double z = anchorSnapshot.child("z").getValue(Double.class);

            Log.d("Firebaselast", "Matched Cloud ID: " + cloudAnchorId + x + y + z + " | Placing anchor...");

            // Place anchor in AR scene
            placeAnchorFromStoredPosition((float) x, (float) y, (float) z);
          }
        }
      }
      @Override
      public void onCancelled(DatabaseError error) {
        Log.e("Firebase", "Error fetching anchor data: " + error.getMessage());
      }
    });
  }*/







  /** Callback function invoked when the user presses the OK button in the Resolve Dialog. */
  private void onRoomCodeEntered(Long roomCode) {
    currentMode = HostResolveMode.RESOLVING;
    hostButton.setEnabled(false);
    resolveButton.setText(R.string.cancel);
    roomCodeText.setText(String.valueOf(roomCode));
    snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_resolve));

    // Instead of fetching from Firebase, directly resolve anchors from predefined list
    resolveAnchorsFromPath();
  }


  /**
   * Listens for both a new room code and an anchor ID, and shares the anchor ID in Firebase with
   * the room code when both are available.
   */
  private final class RoomCodeAndCloudAnchorIdListener
      implements CloudAnchorManager.CloudAnchorHostListener, FirebaseManager.RoomCodeListener {

    private Long roomCode;
    private String cloudAnchorId;

    @Override
    public void onNewRoomCode(Long newRoomCode) {
      Preconditions.checkState(roomCode == null, "The room code cannot have been set before.");
      roomCode = newRoomCode;
      roomCodeText.setText(String.valueOf(roomCode));
      snackbarHelper.showMessageWithDismiss(
          CloudAnchorActivity.this, getString(R.string.snackbar_room_code_available));
      checkAndMaybeShare();
      synchronized (singleTapLock) {
        // Change currentMode to HOSTING after receiving the room code (not when the 'Host' button
        // is tapped), to prevent an anchor being placed before we know the room code and able to
        // share the anchor ID.
        currentMode = HostResolveMode.HOSTING;
      }
    }

    @Override
    public void onError(DatabaseError error) {
      Log.w(TAG, "A Firebase database error happened.", error.toException());
      snackbarHelper.showError(
          CloudAnchorActivity.this, getString(R.string.snackbar_firebase_error));
    }

    @Override
    public void onCloudTaskComplete(String cloudId, CloudAnchorState cloudState) {
      if (cloudState.isError()) {
        Log.e(TAG, "Error hosting a cloud anchor, state " + cloudState);
        snackbarHelper.showMessageWithDismiss(
            CloudAnchorActivity.this, getString(R.string.snackbar_host_error, cloudState));
        return;
      }
      Preconditions.checkState(
          cloudAnchorId == null, "The cloud anchor ID cannot have been set before.");
      cloudAnchorId = cloudId;
      lastPlacedCloudAnchorId = cloudAnchorId;
      storeAnchor(anchor);  // Save the anchor for later distance calculations
      showAnchorIdDialog(roomCode, cloudAnchorId);
      checkAndMaybeShare();
    }




    private void showAnchorIdDialog(Long roomCode, String cloudAnchorId) {
      runOnUiThread(() -> {
        AlertDialog.Builder builder = new AlertDialog.Builder(CloudAnchorActivity.this);
        builder.setTitle("Enter Anchor ID");

        final EditText input = new EditText(CloudAnchorActivity.this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
          String anchorId = input.getText().toString().trim();


          if (!anchorId.isEmpty()) {
            Log.d(TAG, "Saving Anchor - Room: " + roomCode + ", User Anchor ID: " + anchorId + ", Cloud Anchor ID: " + cloudAnchorId);

            firebaseManager.getNearbyAnchors(anchorId, x, y, z, 1.5, nearbyAnchors -> {
              Log.d(TAG, "Nearby Anchors: " + TextUtils.join(", ", nearbyAnchors));
            });

            Log.d(TAG, "Last placed anchor before connecting: " + lastPlacedAnchorId);

            if (lastPlacedAnchorId != null) {
              Log.d(TAG, "Calling connectAnchorsSequentially with: " + lastPlacedAnchorId + " -> " + anchorId);
              connectAnchorsSequentially(lastPlacedAnchorId, anchorId);
            } else {
              Log.d(TAG, "No previous anchor to connect.");
            }

            // Update the last placed anchor
            lastPlacedAnchorId = anchorId;

            firebaseManager.storeAnchorIdInRoom(roomCode, anchorId, cloudAnchorId, x, y, z);

            snackbarHelper.showMessage(CloudAnchorActivity.this, "Anchor ID saved!");
          } else {
            snackbarHelper.showMessage(CloudAnchorActivity.this, "Anchor ID cannot be empty");
          }
        });


        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
      });
    }

    private void connectAnchorsSequentially(String prevAnchorId, String currentAnchorId) {
      DatabaseReference connectionsRef = FirebaseDatabase.getInstance()
              .getReference("anchorConnections");

      // Store bidirectional connections
      connectionsRef.child(prevAnchorId).child(currentAnchorId).setValue(true);
      connectionsRef.child(currentAnchorId).child(prevAnchorId).setValue(true);

      Log.d("AnchorConnections", "Connected: " + prevAnchorId + " <--> " + currentAnchorId);
    }

    private void checkAndMaybeShare() {
      if (roomCode == null || cloudAnchorId == null) {
        return;
      }
      //firebaseManager.storeAnchorIdInRoom(roomCode, cloudAnchorId);
      snackbarHelper.showMessageWithDismiss(
          CloudAnchorActivity.this, getString(R.string.snackbar_cloud_id_shared));
    }
  }

  private final class CloudAnchorResolveStateListener
          implements CloudAnchorManager.CloudAnchorResolveListener {

    @Override
    public void onCloudTaskComplete(Anchor anchor, CloudAnchorState cloudState) {
      if (cloudState.isError()) {
        Log.w("New1", "❌ Error resolving anchor: " + cloudState);
        snackbarHelper.showMessageWithDismiss(
                CloudAnchorActivity.this, getString(R.string.snackbar_resolve_error, cloudState));
        return;
      }

      Log.d("New1", "✅ Successfully resolved anchor: " + anchor.getCloudAnchorId());
      resolvedAnchors.add(anchor);
      setNewAnchor(anchor);
    }

    @Override
    public void onShowResolveMessage() {
      snackbarHelper.setMaxLines(4);
      snackbarHelper.showMessageWithDismiss(
          CloudAnchorActivity.this, getString(R.string.snackbar_resolve_no_result_yet));
    }

    @Override
    public void onShowResolveMessage1() {
      snackbarHelper.setMaxLines(4);
      snackbarHelper.showMessageWithDismiss(
              CloudAnchorActivity.this, getString(R.string.snackbar_wrong_location));
    }
  }





  public void showNoticeDialog(HostResolveListener listener) {
    DialogFragment dialog = PrivacyNoticeDialogFragment.createDialog(listener);
    dialog.show(getSupportFragmentManager(), PrivacyNoticeDialogFragment.class.getName());
  }

  @Override
  public void onDialogPositiveClick(DialogFragment dialog) {
    if (!sharedPreferences.edit().putBoolean(ALLOW_SHARE_IMAGES_KEY, true).commit()) {
      throw new AssertionError("Could not save the user preference to SharedPreferences!");
    }
    createSession();
  }



  public static class AnchorData {
    public Long roomCode;
    public String cloudAnchorid;
    public String anchorId;
    public float x,y,z;

    public AnchorData() {} // Required for Firebase

    public AnchorData(Long roomCode, String cloudAnchorid, String anchorId, float x, float y, float z) {
      this.roomCode = roomCode;
      this.cloudAnchorid = cloudAnchorid;
      this.anchorId = anchorId;
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }



  // PATH FINDING AND GRAPH LOGIC


  public void callingfunctions(String curr, String dest) {
    Log.e("Renderer", "🟢 onResume() called");
    current_location = curr;
    destination_loc = dest;

    // Store a reference to the activity context
    final CloudAnchorActivity activity = CloudAnchorActivity.this;

    pos(new Runnable() {
      @Override
      public void run() {
        getconnections(new Runnable() {
          @Override
          public void run() {
            // Run Astar algorithm
            Astar();
          }
        });
      }
    });
  }


  //For the 3rd parameter of A* (Positions of landmarks)
  private void pos(Runnable onComplete) {
    long startTime = SystemClock.uptimeMillis();
    DatabaseReference detectedImagesRef = FirebaseDatabase.getInstance().getReference("detected_images");
    DatabaseReference anchorsRef = FirebaseDatabase.getInstance().getReference("anchors");

    detectedImagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        List<String> cloudAnchorIds = new ArrayList<>();

        for (DataSnapshot landmarkSnapshot : dataSnapshot.getChildren()) {
          String landmarkName = landmarkSnapshot.getKey();
          String cloudAnchorId = landmarkSnapshot.child("cloudAnchorId").getValue(String.class);

          if (cloudAnchorId != null) {
            cloudAnchorIds.add(cloudAnchorId);
            Log.d("A*", "Landmark: " + landmarkName + ", CloudAnchorId: " + cloudAnchorId);
          }
        }

        // Fetch and store all anchors
        anchorsRef.addListenerForSingleValueEvent(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot anchorSnapshot) {
            for (DataSnapshot anchor : anchorSnapshot.getChildren()) {
              String anchorCloudId = anchor.child("cloudAnchorid").getValue(String.class);

              if (anchorCloudId != null) {
                // Convert manually to float
                double xVal = anchor.child("x").getValue(Double.class);
                double yVal = anchor.child("y").getValue(Double.class);
                double zVal = anchor.child("z").getValue(Double.class);

                float x = (float) xVal;
                float y = (float) yVal;
                float z = (float) zVal;

                // Store in landmarkPositions
                landmarkPositions.put(anchorCloudId, new float[]{x, y, z});

                Log.d("A*", "Stored Anchor: " + anchorCloudId + " -> Position: (" + x + ", " + y + ", " + z + ")");
              }
            }

            Log.e("A*", "Updated LandmarkPositions: " + landmarkPositions);
            onComplete.run(); // Notify completion if needed
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
            Log.e("FirebaseData", "Error fetching anchor data", databaseError.toException());
          }
        });
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        Log.e("FirebaseData", "Error fetching landmark data", databaseError.toException());
      }
    });
  }




  private void getconnections(Runnable onComplete) {
    DatabaseReference connectionsRef = FirebaseDatabase.getInstance().getReference("anchorConnections");
    DatabaseReference anchorsRef = FirebaseDatabase.getInstance().getReference("anchors");

    Map<String, String> anchorToCloudId = new HashMap<>();
    // Remove this line completely - don't create a local variable with the same name as class field
    // Map<String, List<String>> connections = new HashMap<>();

    // Clear existing connections to avoid duplicates
    connections.clear();

    anchorsRef.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        for (DataSnapshot anchor : snapshot.getChildren()) {
          String anchorId = anchor.getKey();
          String cloudAnchorId = anchor.child("cloudAnchorid").getValue(String.class);

          if (cloudAnchorId != null) {
            anchorToCloudId.put(anchorId, cloudAnchorId);
          }
        }

        connectionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            for (DataSnapshot anchorSnapshot : snapshot.getChildren()) {
              String localAnchor = anchorSnapshot.getKey();
              String cloudAnchor = anchorToCloudId.get(localAnchor);

              if (cloudAnchor == null) continue;

              List<String> connectedCloudAnchors = new ArrayList<>();
              for (DataSnapshot connectedAnchor : anchorSnapshot.getChildren()) {
                String connectedLocal = connectedAnchor.getKey();
                String connectedCloud = anchorToCloudId.get(connectedLocal);

                if (connectedCloud != null) {
                  connectedCloudAnchors.add(connectedCloud);
                }
              }

              connections.put(cloudAnchor, connectedCloudAnchors);
            }

            Log.d("A*1", "Cloud Anchor Connections: " + connections);

            // ✅ Call next function when done
            Log.e("connections69", "Connections: "+ connections);
            if (onComplete != null) onComplete.run();
          }

          @Override
          public void onCancelled(DatabaseError error) {
            Log.e("Firebase", "Failed to load anchor connections", error.toException());
          }
        });
      }

      @Override
      public void onCancelled(DatabaseError error) {
        Log.e("Firebase", "Failed to load anchor data", error.toException());
      }
    });
  }



  private void Astar() {

    for (String key : landmarkPositions.keySet()) {
      float[] pos = landmarkPositions.get(key);
      Log.d("DEBUGfuck", "Key: " + key + " -> Position: " + pos[0] + ", " + pos[1] + ", " + pos[2]);
    }


    Log.e("A*LandmarkPosition", "Landmark Positions: " + landmarkPositions);
    for (Map.Entry<String, float[]> entry : landmarkPositions.entrySet()) {
      String key = entry.getKey();
      float[] position = entry.getValue();
      Log.e("LandmarkPosition", "  Key: " + key + ", Position: " + Arrays.toString(position));
    }

    // Correctly log the contents of connections
    Log.e("Connections1", "Connections: " + connections);


    if (landmarkPositions.isEmpty() || connections.isEmpty()) {
      Log.e("A*", "Data not loaded yet! Waiting...");
      return;
    }

    // Check if the specific landmarks exist
    Log.e("A**", "Current location is " + current_location);
    Log.e("A**", "Current location is " + destination_loc);
    Log.e("A*", "Landmark 1 exists in map: " + landmarkPositions.containsKey(current_location));
    Log.e("A*", "Landmark 2 exists in map: " + landmarkPositions.containsKey(destination_loc));

    // Use the found keys, or default to your original ones
    String start = current_location;
    String end = destination_loc;


    Log.e("A*Debug", "🔥 Attempting A* pathfinding...");
    Log.e("A*Debug", "🏁 Start: " + start + ", 🎯 Goal: " + end);

    if (start == null || end == null) {
      Log.e("A*Debug", "❌ Start or End is NULL!");
    } else if (!landmarkPositions.containsKey(start) || !landmarkPositions.containsKey(end)) {
      Log.e("A*Debug", "❌ Start or End landmark NOT found in landmarkPositions!");
    } else if (!connections.containsKey(start) && !connections.containsKey(end)) {
      Log.e("A*Debug", "❌ Start and End are ISOLATED (No connections)!");
    } else {
      Log.e("A*Debug", "✅ All checks passed, calling AStarPathfinder.findShortestPath...");
    }

    try {
      path = AStarPathfinder.findShortestPath(start, end, landmarkPositions, connections);
      Log.e("A*Debug", "The A* path is " + path);

    } catch (Exception e) {
      Log.e("A*Debug", "Error in pathfinding: " + e.getMessage(), e);
    }
  }
  }