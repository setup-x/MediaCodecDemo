package com.jkf.mediacodecdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Bundle;
import android.util.Log;

import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn).setOnClickListener((view -> {
            Intent intent = new Intent(MainActivity.this, MainActivity2.class);
            startActivity(intent);
        }));

        RxPermissions rxPermissions = new RxPermissions(MainActivity.this);
        rxPermissions.requestEach(Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(permission -> {
                    if (permission.granted) {
                    } else if (permission.shouldShowRequestPermissionRationale) {
                        //权限获取失败，但是没有永久拒绝
                    } else {
                        //权限获取失败，而且被永久拒绝
                    }
                });
        chooseVideoEncoder();

        try {
            MediaCodec mediaCodec = MediaCodec.createDecoderByType("video/hevc");

            Log.d("hjyyy", "mediaCodec: " + mediaCodec.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private MediaCodecInfo chooseVideoEncoder() {
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (mci.getName().contains("avc")) {
                    Log.i("hjyyy", String.format("vencoder %s types: %s", mci.getName(), types[j]));
                }

                String[] supportedTypes = mci.getSupportedTypes();
                for (String type : supportedTypes) {
                    MediaCodecInfo.CodecCapabilities capabilities = mci.getCapabilitiesForType(type);

                    int[] colorFormats = capabilities.colorFormats;
                    if (colorFormats.length > 0) {
                        Log.d("hjyyy", "Supported color formats:");
                        for (int format : colorFormats) {
                            Log.d("hjyyy", "  " + format);
                        }
                    } else {
                        Log.d("hjyyy", "No supported color formats");
                    }
                }
            }
        }
        return null;
    }
}