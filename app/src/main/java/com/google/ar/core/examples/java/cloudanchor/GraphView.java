package com.google.ar.core.examples.java.cloudanchor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;


public class GraphView extends View {
    private Map<String, float[]> landmarkPositions = new HashMap<>(); // Stores landmark positions
    private Map<String, List<String>> connections = new HashMap<>(); // Stores connections
    private Paint nodePaint, linePaint, textPaint;

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        nodePaint = new Paint();
        nodePaint.setColor(Color.BLUE);
        nodePaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint();
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(5);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40);
    }

    public void setGraphData(Map<String, float[]> positions, Map<String, List<String>> connections) {
        this.landmarkPositions = positions;
        this.connections = connections;
        invalidate(); // Redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw edges (connections)
        for (Map.Entry<String, List<String>> entry : connections.entrySet()) {
            String startLandmark = entry.getKey();
            float[] startPos = landmarkPositions.get(startLandmark);
            if (startPos == null) continue;

            for (String endLandmark : entry.getValue()) {
                float[] endPos = landmarkPositions.get(endLandmark);
                if (endPos != null) {
                    drawArrow(canvas, startPos[0], startPos[1], endPos[0], endPos[1]);
                }
            }
        }

        // Draw nodes (landmarks)
        for (Map.Entry<String, float[]> entry : landmarkPositions.entrySet()) {
            float[] pos = entry.getValue();
            canvas.drawCircle(pos[0], pos[1], 30, nodePaint);
            canvas.drawText(entry.getKey(), pos[0] + 35, pos[1], textPaint);
        }
    }

    private void drawArrow(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1, y1, x2, y2, linePaint);

        // Draw arrowhead
        float deltaX = x2 - x1;
        float deltaY = y2 - y1;
        float angle = (float) Math.atan2(deltaY, deltaX);
        float arrowSize = 20;

        Path arrowPath = new Path();
        arrowPath.moveTo(x2, y2);
        arrowPath.lineTo((float) (x2 - arrowSize * Math.cos(angle - Math.PI / 6)),
                (float) (y2 - arrowSize * Math.sin(angle - Math.PI / 6)));
        arrowPath.moveTo(x2, y2);
        arrowPath.lineTo((float) (x2 - arrowSize * Math.cos(angle + Math.PI / 6)),
                (float) (y2 - arrowSize * Math.sin(angle + Math.PI / 6)));

        canvas.drawPath(arrowPath, linePaint);
    }
}
