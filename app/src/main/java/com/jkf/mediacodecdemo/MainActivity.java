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

    @SuppressLint("CheckResult")
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

        try {
            MediaCodec mediaCodec = MediaCodec.createDecoderByType("video/avc");
            Log.d("xzc", "mediaCodec: " + mediaCodec.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}