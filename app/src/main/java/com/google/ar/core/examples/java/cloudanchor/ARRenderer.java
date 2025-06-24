package com.google.ar.core.examples.java.cloudanchor;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.Intent;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ARRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "AnchorPlacer";
    private final List<Anchor> anchors = new ArrayList<>();
    private Session arSession;
    private Anchor destinationAnchor = null;
    private boolean destinationReached = false;

    public ARRenderer(Session session) {
        if (session == null) {
            Log.e("ARRenderer", "🔥 Session is NULL in constructor!");
        } else {
            this.arSession = session;
            Log.d("ARRenderer", "✅ AR Session initialized successfully!");
        }
        }
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // OpenGL initialization
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        OpenGLRenderer.initShaderProgram(); // Initialize shaders
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (arSession == null) return;

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        Frame frame;
        try {
            frame = arSession.update();
        } catch (CameraNotAvailableException e) {
            Log.e("ARRenderer", "Camera not available during onDrawFrame", e);
            return;
        }

        Camera camera = frame.getCamera();

        // Get the view and projection matrices
        float[] viewMatrix = new float[16];
        float[] projectionMatrix = new float[16];

        camera.getViewMatrix(viewMatrix, 0);
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

        // ✅ Check distance to destinationAnchor
        if (destinationAnchor != null && !destinationReached) {
            Pose anchorPose = destinationAnchor.getPose();
            Pose cameraPose = camera.getPose();

            float dx = cameraPose.tx() - anchorPose.tx();
            float dy = cameraPose.ty() - anchorPose.ty();
            float dz = cameraPose.tz() - anchorPose.tz();
            float distanceMeters = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distanceMeters < 1.0f) { // within 1 meter
                destinationReached = true;

                Log.d("ARRenderer", "🎯 Destination reached!");

                // Show toast on UI thread
                // Show AlertDialog on UI thread
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.post(() -> {
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MyApplicationContextProvider.getContext());
                    builder.setTitle("🎯 Destination Reached")
                            .setMessage("You’ve successfully reached your destination.")
                            .setCancelable(false)
                            .setPositiveButton("Go Back", (dialog, which) -> {
                                // Optional: launch an activity or close AR session
                                Intent intent = new Intent(MyApplicationContextProvider.getContext(), MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                MyApplicationContextProvider.getContext().startActivity(intent);
                            });

                    android.app.AlertDialog dialog = builder.create();
                    dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY); // For non-activity context
                    dialog.show();
                });
            }
        }

        // Render anchors (these will be drawn on the screen)
        renderAnchors(viewMatrix, projectionMatrix);
    }



    // Function to place anchors along the A* path
    public void placeAnchors(List<float[]> pathPoints) {
        Log.e("whaaa","placeAnchors Called");
        clearAnchors(); // Remove existing anchors before placing new ones

        if (arSession == null) {
            Log.e(TAG, "AR Session is NULL! Cannot place anchors.");
            return;
        }

        for (int i = 0; i < pathPoints.size(); i++) {
            float[] point = pathPoints.get(i);
            Pose anchorPose = new Pose(point, new float[]{0, 0, 0, 1});
            Anchor anchor = arSession.createAnchor(anchorPose);
            anchors.add(anchor);

            if (i == pathPoints.size() - 1) {
                destinationAnchor = anchor;
                destinationReached = false; // reset flag
            }

            Log.d(TAG, "Anchor placed at: " + point[0] + ", " + point[1] + ", " + point[2]);
        }
    }

    // Function to clear previous anchors
    public void clearAnchors() {
        for (Anchor anchor : anchors) {
            anchor.detach();
        }
        anchors.clear();
    }



    // Function to render anchors in OpenGL
    public void renderAnchors(float[] viewMatrix, float[] projectionMatrix) {
        GLES20.glUseProgram(OpenGLRenderer.shaderProgram);

        // Get current camera height (Y-axis)
        float cameraY = 0f;
        try {
            Frame frame = arSession.update();
            Pose cameraPose = frame.getCamera().getPose();
            cameraY = cameraPose.ty();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available for height check", e);
            return;
        }

        for (Anchor anchor : anchors) {
            Pose pose = anchor.getPose();
            float anchorY = pose.ty();
            float heightDifference = Math.abs(cameraY - anchorY);

            if (heightDifference < 1.5f) {  // Only render anchors within 1.5 meters vertically
                float[] modelMatrix = new float[16];
                pose.toMatrix(modelMatrix, 0);
                OpenGLRenderer.drawAnchorAtPosition(modelMatrix, viewMatrix, projectionMatrix);
            } else {
                Log.d(TAG, "Skipping anchor due to height diff: " + heightDifference);
            }
        }
    }

    // Java side (MainActivity.java)

}
