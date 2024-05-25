package com.jkf.mediacodecdemo;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class H265Decoder {

    private static final String MIME_TYPE = "video/avc";
    private static final int TIMEOUT_US = 10000;

    private static final String TAG = "H265Decoder";
    private MediaCodec mediaCodec;
    private Surface surface;
    private HandlerThread decoderThread;
    private Handler decoderHandler;
    private Context context;
    private boolean isVideoSizeSet = false;
    private DecoderCallback callback;
    private boolean dequeueRunning;

    private VideoFramePull videoFramePull;
    private int videoWidth;
    private int videoHeight;


    public H265Decoder(Context context, Surface surface, H265Decoder.DecoderCallback callback) {
        this.callback = callback;
        this.surface = surface;
        this.context = context;
    }

    public void startDecoding(final String filePath) {
        decoderThread = new HandlerThread("DecoderThread");
        decoderThread.start();
        decoderHandler = new Handler(decoderThread.getLooper());

        decoderHandler.post(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void run() {
                try {
                    initDecoder();
                    decodeRawH265Stream(filePath);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    isVideoSizeSet = false;
                }
            }
        });
    }


    public void stop() {
        dequeueRunning = false;

        try {
            if (videoFramePull != null) {
                videoFramePull.join(500);
            }

            if (decoderThread != null) {
                decoderThread.quitSafely();
                decoderHandler = null;
            }

            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            videoFramePull = null;
            decoderThread = null;
            mediaCodec = null;
        }
        Log.d(TAG, "stop. ");
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void initDecoder() throws IOException {
        int width = 1920;
        int height = 1080;

        // 创建解码器
        mediaCodec = MediaCodec.createDecoderByType(MIME_TYPE);

        // 创建并配置媒体格式
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

        // 计算最大输入大小
        int maxInputSize = calculateMaxInputSize(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);

        // 配置解码器
        mediaCodec.configure(format, surface, null, 0);

        // 检查实际的缓冲区大小
        MediaFormat outputFormat = mediaCodec.getOutputFormat();
        int actualMaxInputSize = outputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);

        Log.d(TAG, "Requested max input size: " + maxInputSize);
        Log.d(TAG, "Actual max input size: " + actualMaxInputSize);

        // 启动解码器
        mediaCodec.start();

        // 启动视频帧提取线程
        videoFramePull = new VideoFramePull();
        videoFramePull.start();
    }


    private void decodeRawH265Stream(String filePath) throws IOException {
        try {
            //1、IO流方式读取h264文件【太大的视频分批加载】
            byte[] bytes = null;
            bytes = getBytes(filePath);
            //2、拿到 mediaCodec 所有队列buffer[]
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            //开始位置
            int startIndex = 0;
            //h264总字节数
            int totalSize = bytes.length;

            //3、解析
            while (dequeueRunning) {
                //判断是否符合
                if (totalSize == 0 || startIndex >= totalSize) {
                    break;
                }
                //寻找索引
                int nextFrameStart = findByFrame(bytes, startIndex + 1, totalSize);
                if (nextFrameStart == -1) break;

                // 查询10000毫秒后，如果dSP芯片的buffer全部被占用，返回-1；存在则大于0
                int inIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_US);
                if (inIndex >= 0) {
                    //根据返回的index拿到可以用的buffer
                    ByteBuffer byteBuffer = inputBuffers[inIndex];
                    //清空byteBuffer缓存
                    byteBuffer.clear();
                    //开始为buffer填充数据
                    byteBuffer.put(bytes, startIndex, nextFrameStart - startIndex);
                    //填充数据后通知mediacodec查询inIndex索引的这个buffer,
                    mediaCodec.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0);
                } else {
                    Log.d(TAG, "dequeueInputBuffer fail. ");
                    mediaCodec.flush();
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                startIndex = nextFrameStart;

            }

            if (videoFramePull != null) {
                dequeueRunning = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    //读取一帧数据
    private int findByFrame(byte[] bytes, int start, int totalSize) {
        for (int i = start; i < totalSize - 4; i++) {
            //对output.h264文件分析 可通过分隔符 0x00000001 读取真正的数据
            if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x00 && bytes[i + 3] == 0x01) {
                return i;
            }
        }
        return -1;
    }

    //读取一帧数据
    private boolean findByVpsFrame(byte[] bytes, int start, int totalSize) {
        int i = start;
        if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x00 && bytes[i + 3] == 0x01 && bytes[i + 4] == 0x40) {
            return true;
        }
        return false;
    }

    private boolean findBySpsFrame(byte[] bytes, int start, int totalSize) {
        int i = start;
        if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x00 && bytes[i + 3] == 0x01 && bytes[i + 4] == 0x42) {
            return true;
        }
        return false;
    }

    private boolean findByPpsFrame(byte[] bytes, int start, int totalSize) {
        int i = start;
        if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x00 && bytes[i + 3] == 0x01 && bytes[i + 4] == 0x44) {
            return true;
        }
        return false;
    }

    private byte[] getBytes(String videoPath) throws IOException {
        AssetManager assetManager = context.getAssets();
        InputStream is = assetManager.open(videoPath, AssetManager.ACCESS_STREAMING);
        int len;
        int size = 1024;
        byte[] buf;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        buf = new byte[size];
        while ((len = is.read(buf, 0, size)) != -1) {
            bos.write(buf, 0, len);
            /*if (bos.size() > 50000000) {
                break;
            }*/
        }
        buf = bos.toByteArray();
        return buf;
    }


    public void readFileBySize(String filePath, int w, int h) throws IOException {
        FileInputStream fis = new FileInputStream(filePath);
        int bytesRead = w * h * 3 / 2;
        byte[] buffer = new byte[bytesRead];


        while ((bytesRead = fis.read(buffer)) != -1) {
            Log.d("xzc", "readFileBySize: " + bytesRead);
        }

        fis.close();
    }

    public interface DecoderCallback {
        void onVideoSizeChanged(int width, int height);
    }

    public class VideoFramePull extends Thread {
        @Override
        public void run() {
            dequeueRunning = true;
            while (dequeueRunning && mediaCodec != null) {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                try {
                    int outIndex = mediaCodec.dequeueOutputBuffer(info, TIMEOUT_US * 20);
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.d(TAG, "dequeueOutput timeout. ");
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "MediaCodec.INFO_OUTPUT_FORMAT_CHANGED. ");
                    } else if (outIndex >= 0) {
                        if (mediaCodec != null) {
                            if (!isVideoSizeSet) {
                                //获取视频的实际宽度和高度
                                MediaFormat outputFormat = mediaCodec.getOutputFormat();
                                videoWidth = outputFormat.getInteger(MediaFormat.KEY_WIDTH);
                                videoHeight = outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
                                Log.d("xzc", "format: " + outputFormat + "\nvideoWidth: " + videoWidth + ",videoHeight: " + videoHeight);
                                Log.d("xzc", "YUV size: " + info.size);
                                if (callback != null) {
                                    callback.onVideoSizeChanged(videoWidth, videoHeight);
                                }
                                isVideoSizeSet = true;
                            }
                            mediaCodec.releaseOutputBuffer(outIndex, true);

                        }
                    } else {
                        Log.d("xzc", "outIndex < 0.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 计算最大输入大小的方法
    private int calculateMaxInputSize(String mimeType, int width, int height) {
        int frameSize;
        if ("video/avc".equals(mimeType)) { // 假设使用 YUV420 格式
            frameSize = width * height * 3 / 2;
        } else {
            // 其他格式根据需要调整
            frameSize = width * height * 4;
        }

        // 假设缓冲区最多处理 10 帧数据
        int maxInputSize = frameSize * 10;
        return maxInputSize;
    }

}

