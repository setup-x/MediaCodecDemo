#ifndef OPENGLRENDER_H
#define OPENGLRENDER_H

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl3.h> // 添加此头文件
#include "native.h"


class OpenGLRender {
public:
    OpenGLRender();

    ~OpenGLRender();

    void init(JNIEnv *env, jint texid, jobject surface, int w, int h, EGLContext ctx);

    void unInit();

    void renderFrame();

private:
    void loadShader(GLenum type, const char *shaderCode, GLuint *shader);

    void release();

private:
    GLuint program;
    GLuint texId;
    GLint textureUniform;
    GLint positionAttribute;
    GLint texCoordAttribute;
    GLuint vbo;
    GLuint vao; // VAO id
    bool useVao; // Use VAO flag
    int width;
    int height;
    EGLDisplay display;
    EGLSurface windowSurface;
    EGLContext context;

    static const char *vertexShaderSource;
    static const char *fragmentShaderSource;
    static const GLfloat vertexVertices[];
    static const GLfloat texVertices[];
};

#endif // OPENGLRENDER_H
