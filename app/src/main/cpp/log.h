//
// Created by hq on 2019/8/12.
//

#ifndef UDPDEMO_LOG_H
#define UDPDEMO_LOG_H

#include <android/Log.h>

#define   LOG_TAG    "JNI"
#define   LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define   LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define   LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define   LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define   ALOGD LOGD
#define   ALOGI LOGI
#define   ALOGW LOGW
#define   ALOGE LOGE

#endif //UDPDEMO_LOG_H
