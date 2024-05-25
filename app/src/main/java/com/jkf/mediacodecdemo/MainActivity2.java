package com.jkf.mediacodecdemo;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity2 extends AppCompatActivity {

    private SurfaceView surfaceView;

    private MyGLSurfaceView glSurfaceView;

    private H265Decoder h265Decoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        glSurfaceView = new MyGLSurfaceView(MainActivity2.this);
        setContentView(glSurfaceView);
//        setContentView(R.layout.activity_main2);
//        surfaceView = findViewById(R.id.surface);
//        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(@NonNull SurfaceHolder holder) {
//                H265Decoder h265Decoder = new H265Decoder(MainActivity2.this, holder.getSurface(), new H265Decoder.DecoderCallback() {
//                    @Override
//                    public void onVideoSizeChanged(int width, int height) {
//                        Log.d("xzc", "width: " + width + " ,height:" + height);
//                    }
//                });
//                h265Decoder.startDecoding("MiracastDemo.h264");
//            }
//
//            @Override
//            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
//
//            }
//
//            @Override
//            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
//
//            }
//        });

    }

    @Override
    protected void onPostResume() {
        new Thread(() -> {
            h265Decoder = new H265Decoder(MainActivity2.this, glSurfaceView.getDecoderSurface(), new H265Decoder.DecoderCallback() {
                @Override
                public void onVideoSizeChanged(int width, int height) {
                    //glSurfaceView.setView(width, height);
                }
            });
            h265Decoder.startDecoding("MiracastDemo.h264");
        }).start();
        super.onPostResume();
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                h265Decoder.stop();
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
//        glSurfaceView.release();
        super.onDestroy();
    }

}