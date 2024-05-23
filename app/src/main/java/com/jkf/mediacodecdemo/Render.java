package com.jkf.mediacodecdemo;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;

public class Render {
    static {
        System.loadLibrary("yuv_render-lib");
    }

    private static final String TAG = "Render";

    private native long glInit(int texid, Surface surface, int w, int h, long context);

    private native void glUninit(long ptr);

    private native void renderFrame(long ptr);

    private long renderPtr = -1;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private Surface decoderSurface;

    private EGLContext eglContext;

    private int texId;

    private int resolutionW;
    private int resolutionH;

    private boolean isSetup = false;

    Render(Surface surface, int resolutionW, int resolutionH) {
        this.resolutionW = resolutionW;
        this.resolutionH = resolutionH;

        texId = createExternalTexture();
        surfaceTexture = new SurfaceTexture(texId);
        decoderSurface = new Surface(surfaceTexture);
        surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                // 当帧可用时，触发渲染
                if (isSetup) {
                    init(surface, resolutionW, resolutionH);
                    isSetup = false;
                }
                updateTexImage();
            }
        });
        decoderSurface = new Surface(surfaceTexture);
        eglContext = EGL14.eglGetCurrentContext();
    }

    public void setup(int resolutionW, int resolutionH) {
        this.resolutionW = resolutionW;
        this.resolutionH = resolutionH;
        isSetup = true;
    }

    public void init(Surface surface, int w, int h) {
        this.surface = surface;
        renderPtr = glInit(texId, this.surface, w, h, eglContext.getNativeHandle());
    }

    public void uninit() {
        if (decoderSurface != null) {
            decoderSurface.release();
            decoderSurface = null;
        }

        if (surfaceTexture != null) {
            surfaceTexture.setOnFrameAvailableListener(null);
            surfaceTexture.release();
            surfaceTexture = null;
        }

        glUninit(renderPtr);
        isSetup = false;
        renderPtr = -1;
        releaseExternalTexture(texId);
    }

    public Surface getSurface() {
        return decoderSurface;
    }

    public void updateTexImage() {
        if (renderPtr != -1) {
            surfaceTexture.updateTexImage();
            renderFrame(renderPtr);
        }
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


}
