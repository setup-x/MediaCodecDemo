package com.jkf.mediacodecdemo;


import static android.opengl.ETC1Util.createTexture;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.ETC1Util;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class MyGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "MyGLSurfaceView";
    private OpenGLHelper openGLHelper;
    private SurfaceTexture surfaceTexture;
    private Surface decoderSurface;

    private AtomicBoolean updateSurface = new AtomicBoolean(false);

    private Semaphore semaphore = new Semaphore(0);
    private int texId;

    public MyGLSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setup();
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    private void setup() {
        texId = createExternalTexture();
        surfaceTexture = new SurfaceTexture(texId);
        decoderSurface = new Surface(surfaceTexture);
        HandlerThread handlerThread = new HandlerThread("FrameHandlerThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        surfaceTexture.setOnFrameAvailableListener(this, handler);
        openGLHelper = new OpenGLHelper();
    }

    public Surface getDecoderSurface() {
        return decoderSurface;
    }

    public void setView(int videoWidth, int videoHeight) {
        openGLHelper.setSurfaceSize(videoWidth, videoHeight);
    }

    private int createExternalTexture() {
        int[] textureId = new int[1];
        GLES20.glGenTextures(1, textureId, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return textureId[0];
    }

    private void releaseExternalTexture(int texId) {
        GLES20.glDeleteTextures(1, new int[]{texId}, 0);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onFrameAvailable: " + surfaceTexture.getTimestamp());
        updateSurface.set(true);
        requestRender(); // 请求渲染新的帧
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        Log.d("xzc", "onSurfaceCreated: ");
        openGLHelper.setup(texId);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int i, int i1) {
        openGLHelper.setSurfaceSize(i, i1);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        if (updateSurface.getAndSet(false)) {
            surfaceTexture.updateTexImage();
        }
        openGLHelper.draw();
    }


    private class OpenGLHelper {

        private final String vertexShaderCode =
                "attribute vec4 aPosition;" +
                        "attribute vec2 aTexCoord;" +
                        "varying vec2 vTexCoord;" +
                        "void main() {" +
                        "  gl_Position = aPosition;" +
                        "  vTexCoord = aTexCoord;" +
                        "}";

        private final String fragmentShaderCode =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;" +
                        "varying vec2 vTexCoord;" +
                        "uniform samplerExternalOES sTexture;" +
                        "void main() {" +
                        "  gl_FragColor = texture2D(sTexture, vTexCoord);" +
                        "}";


        private final float[] squareCoords = {
                -1.0f, 1.0f, 0.0f,
                -1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f
        };

        private final float[] texCoords = {
                0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f,
                1.0f, 0.0f
        };

        private int mProgram;
        private int mPositionHandle;
        private int mTexCoordHandle;
        private int mTextureHandle;

        private final FloatBuffer vertexBuffer;
        private final FloatBuffer texCoordBuffer;

        public OpenGLHelper() {
            ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(squareCoords);
            vertexBuffer.position(0);

            ByteBuffer tb = ByteBuffer.allocateDirect(texCoords.length * 4);
            tb.order(ByteOrder.nativeOrder());
            texCoordBuffer = tb.asFloatBuffer();
            texCoordBuffer.put(texCoords);
            texCoordBuffer.position(0);
        }

        public void setup(int textureId) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

            mProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mProgram, vertexShader);
            GLES20.glAttachShader(mProgram, fragmentShader);
            GLES20.glLinkProgram(mProgram);

            mPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
            mTextureHandle = GLES20.glGetUniformLocation(mProgram, "sTexture");

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(mTextureHandle, 0);
        }

        public void draw() {
            long startTime = System.currentTimeMillis();

            GLES20.glUseProgram(mProgram);

            GLES20.glEnableVertexAttribArray(mPositionHandle);
            GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

            GLES20.glEnableVertexAttribArray(mTexCoordHandle);
            GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);

            GLES20.glDisableVertexAttribArray(mPositionHandle);
            GLES20.glDisableVertexAttribArray(mTexCoordHandle);

            long endTime = System.currentTimeMillis();
            Log.d(TAG, "draw: Frame time = " + (endTime - startTime) + " ms");
        }


        public void setSurfaceSize(int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            return shader;
        }
    }

    public void release() {

        releaseExternalTexture(texId);
    }

}

