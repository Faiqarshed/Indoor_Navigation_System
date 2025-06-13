package com.google.ar.core.examples.java.cloudanchor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;

import java.util.*;

public class GraphActivity extends AppCompatActivity {
    private DatabaseReference graphRef;
    private Map<String, LandmarkNode> navigationGraph = new HashMap<>();
    private GraphView graphView;
    private CloudAnchorActivity forpos;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        graphView = new GraphView(this);
        setContentView(graphView);
        Log.e("Cunt69","Graph Activity executed");
        fetchNavigationGraph();

        forpos = new CloudAnchorActivity(); // Initialize the object
        // Get the root layout of your activity
        FrameLayout rootLayout = findViewById(android.R.id.content);

        // Create the "Done" button
        Button btnDone = new Button(this);
        btnDone.setText("Perform A*");
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

        // Set click listener for button
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (forpos != null) {
                    forpos.callingfunctions();
                    Intent intent = new Intent(GraphActivity.this, CloudAnchorActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish(); // Close the current activity

                } else {
                    Log.e("GraphView", "forpos is null!");
                }

            }
        });
    }



    private void fetchNavigationGraph() {
        graphRef = FirebaseDatabase.getInstance().getReference("navigationGraph");

        // Corrected: Using event listener instead of .get()
        graphRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.e("GraphVisualization", "Navigation graph is empty!");
                    return;
                }

                Log.d("GraphVisualization", "Firebase graph exists!");

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String landmarkId = snapshot.getKey();
                    List<String> connectedLandmarks = new ArrayList<>();
                    List<String> path = new ArrayList<>();

                    if (snapshot.child("connectedLandmarks").exists()) {
                        for (DataSnapshot child : snapshot.child("connectedLandmarks").getChildren()) {
                            String value = child.getValue(String.class);
                            if (value != null) {
                                connectedLandmarks.add(value);
                            }
                        }
                    }

                    if (snapshot.child("path").exists()) {
                        for (DataSnapshot child : snapshot.child("path").getChildren()) {
                            String value = child.getValue(String.class);
                            if (value != null) {
                                path.add(value);
                            }
                        }
                    }

                    Log.d("GraphVisualization", "Loaded node: " + landmarkId +
                            " | Connected: " + connectedLandmarks +
                            " | Path: " + path);

                    navigationGraph.put(landmarkId, new LandmarkNode(landmarkId, connectedLandmarks, path));
                }

                Log.d("GraphVisualization", "Total nodes loaded: " + navigationGraph.size());

                runOnUiThread(() -> {
                    graphView.setGraph(navigationGraph);
                    graphView.invalidate();
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("GraphVisualization", "Failed to fetch navigation graph", databaseError.toException());
                runOnUiThread(() ->
                        Toast.makeText(GraphActivity.this, "Failed to load graph. Check internet.", Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    public static class LandmarkNode {
        public String landmarkId;
        public List<String> connectedLandmarks;
        public List<String> path;

        public LandmarkNode(String landmarkId, List<String> connections, List<String> paths) {
            this.landmarkId = landmarkId;
            this.connectedLandmarks = connections;
            this.path = paths;
        }
    }

    // Custom View for Drawing the Graph
    private class GraphView extends View {
        private Paint nodePaint, textPaint, linePaint;
        private Map<String, Float[]> nodePositions = new HashMap<>();

        public GraphView(Context context) {
            super(context);
            initPaints();
        }

        private void initPaints() {
            nodePaint = new Paint();
            nodePaint.setColor(Color.BLUE);
            nodePaint.setStyle(Paint.Style.FILL);

            textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(40f);
            textPaint.setTextAlign(Paint.Align.CENTER);

            linePaint = new Paint();
            linePaint.setColor(Color.RED);
            linePaint.setStrokeWidth(5f);
        }

        public void setGraph(Map<String, LandmarkNode> graph) {
            navigationGraph = graph;
            generateNodePositions();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            generateNodePositions();
        }

        private void generateNodePositions() {
            int width = getWidth();
            int height = getHeight();
            if (width == 0 || height == 0) return;

            int cols = (int) Math.ceil(Math.sqrt(navigationGraph.size()));
            int row = 0, col = 0;
            int margin = 100;

            for (String landmark : navigationGraph.keySet()) {
                float x = col * (width / cols) + margin;
                float y = row * (height / cols) + margin;
                nodePositions.put(landmark, new Float[]{x, y});

                col++;
                if (col >= cols) {
                    col = 0;
                    row++;
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (navigationGraph.isEmpty()) return;

            for (Map.Entry<String, LandmarkNode> entry : navigationGraph.entrySet()) {
                String landmarkId = entry.getKey();
                LandmarkNode node = entry.getValue();
                Float[] startPos = nodePositions.get(landmarkId);

                if (startPos == null) continue;

                for (String connectedLandmark : node.connectedLandmarks) {
                    Float[] endPos = nodePositions.get(connectedLandmark);
                    if (endPos != null) {
                        drawArrow(canvas, startPos[0], startPos[1], endPos[0], endPos[1]);
                    }
                }
            }

            for (Map.Entry<String, LandmarkNode> entry : navigationGraph.entrySet()) {
                String landmarkId = entry.getKey();
                LandmarkNode node = entry.getValue();
                List<String> path = node.path;

                for (int i = 0; i < path.size() - 1; i++) {
                    Float[] anchorPos1 = nodePositions.get(path.get(i));
                    Float[] anchorPos2 = nodePositions.get(path.get(i + 1));

                    if (anchorPos1 != null && anchorPos2 != null) {
                        drawArrow(canvas, anchorPos1[0], anchorPos1[1], anchorPos2[0], anchorPos2[1]);
                    }
                }
            }

            for (Map.Entry<String, Float[]> entry : nodePositions.entrySet()) {
                Float[] pos = entry.getValue();
                canvas.drawCircle(pos[0], pos[1], 40, nodePaint);
                canvas.drawText(entry.getKey(), pos[0], pos[1] - 50, textPaint);
            }
        }

        private void drawArrow(Canvas canvas, float startX, float startY, float endX, float endY) {
            canvas.drawLine(startX, startY, endX, endY, linePaint);

            float deltaX = endX - startX;
            float deltaY = endY - startY;
            float angle = (float) Math.atan2(deltaY, deltaX);
            float arrowSize = 80;

            float arrowX1 = endX - arrowSize * (float) Math.cos(angle - Math.PI / 6);
            float arrowY1 = endY - arrowSize * (float) Math.sin(angle - Math.PI / 6);

            float arrowX2 = endX - arrowSize * (float) Math.cos(angle + Math.PI / 6);
            float arrowY2 = endY - arrowSize * (float) Math.sin(angle + Math.PI / 6);

            Log.d("GraphView", "Arrow from (" + startX + "," + startY + ") to (" + endX + "," + endY + ")");
            Log.d("GraphView", "Arrowhead points: (" + arrowX1 + "," + arrowY1 + ") and (" + arrowX2 + "," + arrowY2 + ")");

            canvas.drawLine(endX, endY, arrowX1, arrowY1, linePaint);
            canvas.drawLine(endX, endY, arrowX2, arrowY2, linePaint);
        }
    }


}
