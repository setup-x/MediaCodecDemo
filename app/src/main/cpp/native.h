//
// Created by hq on 2019/8/13.
//

#ifndef RENDER_NATIVE_H
#define RENDER_NATIVE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong

JNICALL
Java_com_jkf_mediacodecdemo_Render_glInit(JNIEnv *env, jobject thiz, jint texid,
                                          jobject surface, jint yuv_width,
                                          jint yuv_height, jlong eglContext);

JNIEXPORT void JNICALL
Java_com_jkf_mediacodecdemo_Render_glUninit(JNIEnv
                                            *env,
                                            jobject thiz, jlong
                                            glContextPtr);

JNIEXPORT void JNICALL
Java_com_jkf_mediacodecdemo_Render_renderFrame(JNIEnv
                                               *env,
                                               jobject thiz, jlong
                                               glContextPtr);

#ifdef __cplusplus
}
#endif

#endif //RENDER_NATIVE_H
