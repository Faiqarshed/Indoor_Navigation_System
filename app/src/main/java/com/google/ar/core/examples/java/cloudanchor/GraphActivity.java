package com.google.ar.core.examples.java.cloudanchor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private String curr;
    private String destination;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Main root as FrameLayout (absolute positioning)
        FrameLayout root = new FrameLayout(this);
        setContentView(root);

        // TITLE TextView
        TextView titleText = new TextView(this);
        titleText.setText("The Graph for your destination");
        titleText.setTextSize(24f);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setTextColor(Color.BLACK);
        titleText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        titleParams.topMargin = 950;
        root.addView(titleText, titleParams);

        // GRAPH View (Centered)
        graphView = new GraphView(this);
        FrameLayout.LayoutParams graphParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        graphParams.topMargin = 200;
        graphParams.bottomMargin = 200;
        root.addView(graphView, graphParams);

        // Perform A* Button (Bottom Center)
        Button btnDone = new Button(this);
        btnDone.setText("Perform A*");
        btnDone.setBackgroundColor(Color.BLUE);
        btnDone.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        buttonParams.bottomMargin = 300;
        root.addView(btnDone, buttonParams);

        // Get Intent Data
        Intent intent = getIntent();
        curr = intent.getStringExtra("CURRENT_LOCATION");
        destination = intent.getStringExtra("DESTINATION_LOCATION");

        Log.d("GraphActivity222", "Received current: " + curr + ", destination: " + destination);

        fetchNavigationGraph();

        forpos = new CloudAnchorActivity();
        btnDone.setOnClickListener(v -> {
            if (forpos != null) {
                Intent i = new Intent(GraphActivity.this, CloudAnchorActivity.class);
                i.putExtra("current_location", curr);
                i.putExtra("destination_location", destination);
                forpos.callingfunctions(curr, destination);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            } else {
                Log.e("GraphView", "forpos is null!");
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

            int totalNodes = navigationGraph.size();
            int cols = (int) Math.ceil(Math.sqrt(totalNodes));
            int rows = (int) Math.ceil((double) totalNodes / cols);
            int nodeSpacingX = width / (cols + 1);
            int nodeSpacingY = height / (rows + 1);

            List<String> landmarks = new ArrayList<>(navigationGraph.keySet());
            int index = 0;

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (index >= landmarks.size()) break;

                    float x = (col + 1) * nodeSpacingX;
                    float y = (row + 1) * nodeSpacingY;

                    nodePositions.put(landmarks.get(index), new Float[]{x, y});
                    index++;
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
