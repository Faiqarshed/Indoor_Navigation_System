package com.google.ar.core.examples.java.cloudanchor;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

public class OpenGLRenderer {
    private static final String TAG = "OpenGLRenderer";
    public static int shaderProgram;

    // Vertex Shader Code
    private static final String VERTEX_SHADER_CODE =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    // Fragment Shader Code
    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";

    private static FloatBuffer vertexBuffer;
    private static final int COORDS_PER_VERTEX = 3;
    private static float[] anchorCoords = {
            0.0f,  0.1f, 0.0f,  // Top vertex
            -0.1f, -0.1f, 0.0f,  // Bottom left
            0.1f, -0.1f, 0.0f   // Bottom right
    };

    private static final int vertexCount = anchorCoords.length / COORDS_PER_VERTEX;


    static {
        ByteBuffer bb = ByteBuffer.allocateDirect(anchorCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(anchorCoords);
        vertexBuffer.position(0);
    }

    public static void initShaderProgram() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);

        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertexShader);
        GLES20.glAttachShader(shaderProgram, fragmentShader);
        GLES20.glLinkProgram(shaderProgram);
    }

    private static int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    public static void drawAnchorAtPosition(float[] anchorMatrix, float[] viewMatrix, float[] projectionMatrix) {
        Log.d("Drawit", "Drawing Anchor at Position: " + Arrays.toString(anchorMatrix));

        GLES20.glUseProgram(shaderProgram);

        // Get attribute locations
        int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        int mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");

        // Compute MVP Matrix
        float[] mvpMatrix = new float[16];
        float[] modelViewMatrix = new float[16];

        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, anchorMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

        // Pass the MVP matrix to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        // Enable vertex array
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // Draw the object
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(positionHandle);
    }


}

