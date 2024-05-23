#include <android/native_window_jni.h>
#include "OpenGLRender.h"


extern "C" JNIEXPORT jlong JNICALL
Java_com_jkf_mediacodecdemo_Render_glInit(JNIEnv *env, jobject thiz, jint texid,
                                          jobject surface, jint yuv_width,
                                          jint yuv_height, jlong eglContext) {
    EGLContext context = reinterpret_cast<EGLContext>(eglContext);
    OpenGLRender *openGLRender = new OpenGLRender();
    openGLRender->init(env, texid, surface, yuv_width, yuv_height, context);// 添加这一行保存 EGL 上下文
    return reinterpret_cast<jlong>(openGLRender);
}


extern "C" JNIEXPORT void JNICALL
Java_com_jkf_mediacodecdemo_Render_glUninit(JNIEnv *env, jobject thiz,
                                            jlong glContextPtr) {
    if (glContextPtr != 0) {
        OpenGLRender *render = reinterpret_cast<OpenGLRender *>(glContextPtr);
        render->unInit();
        delete render;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_jkf_mediacodecdemo_Render_renderFrame(JNIEnv *env, jobject thiz,
                                               jlong glContextPtr) {
    if (glContextPtr != 0) {
        OpenGLRender *render = reinterpret_cast<OpenGLRender *>(glContextPtr);
        render->renderFrame();
    }
}
