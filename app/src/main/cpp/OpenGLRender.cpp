// OpenGLRender.cpp
#include "OpenGLRender.h"
#include "log.h"
#include <sys/time.h>

const char *OpenGLRender::vertexShaderSource = R"(
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            vTexCoord = vec2(aTexCoord.x, 1.0 - aTexCoord.y); // 反转纹理的 y 坐标
            gl_Position = aPosition;
        }
)";

const char *OpenGLRender::fragmentShaderSource = R"(
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES texture;
        void main() {
            gl_FragColor = texture2D(texture, vTexCoord);
        }
)";

const GLfloat OpenGLRender::vertexVertices[] = {
        1.0f, -1.0f, 0.0f,
        -1.0f, -1.0f, 0.0f,
        1.0f, 1.0f, 0.0f,
        -1.0f, 1.0f, 0.0f,
};

const GLfloat OpenGLRender::texVertices[] = {
        1.0f, 0.0f, // 右下
        0.0f, 0.0f,
        1.0f, 1.0f,
        0.0f, 1.0f
};

OpenGLRender::OpenGLRender() : width(0), height(0), display(EGL_NO_DISPLAY),
                               windowSurface(EGL_NO_SURFACE), context(EGL_NO_CONTEXT),
                               program(0), textureUniform(0), positionAttribute(0),
                               texCoordAttribute(0), texId(0), vao(0) {}

OpenGLRender::~OpenGLRender() {
    release();
}

void OpenGLRender::loadShader(GLenum type, const char *shaderCode, GLuint *shader) {
    // 创建着色器
    *shader = glCreateShader(type);
    if (*shader == 0) {
        LOGE("glCreateShader failed");
        return;
    }

    // 加载着色器源代码
    glShaderSource(*shader, 1, &shaderCode, nullptr);
    glCompileShader(*shader);

    // 检查着色器编译状态
    GLint compileStatus = 0;
    glGetShaderiv(*shader, GL_COMPILE_STATUS, &compileStatus);
    if (compileStatus == GL_FALSE) {
        GLint logLength = 0;
        glGetShaderiv(*shader, GL_INFO_LOG_LENGTH, &logLength);
        if (logLength > 0) {
            char *log = new char[logLength];
            glGetShaderInfoLog(*shader, logLength, &logLength, log);
            LOGE("Shader compilation error: %s", log);
            delete[] log;
        }
        glDeleteShader(*shader);
        *shader = 0;
    }
}

void OpenGLRender::init(JNIEnv *env, jint texid, jobject surface, int w, int h, EGLContext ctx) {
    width = w;
    height = h;

    // 获取默认显示设备
    display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed!");
        release();
        return;
    }

    // 初始化EGL
    if (EGL_TRUE != eglInitialize(display, nullptr, nullptr)) {
        LOGE("eglInitialize failed!");
        release();
        return;
    }

    // 配置EGL属性
    EGLint numConfigs;
    EGLConfig config;
    const EGLint configAttribs[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_NONE
    };
    if (!eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) || numConfigs == 0) {
        LOGE("eglChooseConfig failed or no valid config found!");
        release();
        return;
    }

    // 创建EGL窗口
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    windowSurface = eglCreateWindowSurface(display, config, window, nullptr);
    if (windowSurface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed!");
        release();
        return;
    }

    // 创建EGL上下文
    EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    context = eglCreateContext(display, config, ctx,
                               contextAttributes); // Use the passed EGLContext
    if (context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed!");
        release();
        return;
    }

    if (EGL_TRUE != eglMakeCurrent(display, windowSurface, windowSurface, context)) {
        LOGE("eglMakeCurrent failed! %d", glGetError());
        release();
        return;
    }

    texId = texid;
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, texId);

    // 编译链接着色器
    GLuint vertexShader, fragmentShader;
    loadShader(GL_VERTEX_SHADER, vertexShaderSource, &vertexShader);
    loadShader(GL_FRAGMENT_SHADER, fragmentShaderSource, &fragmentShader);

    // 创建着色器程序
    program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);

    // 检查着色器程序链接状态
    GLint linkStatus;
    glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
    if (linkStatus != GL_TRUE) {
        GLint logLength = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &logLength);
        if (logLength > 0) {
            char *log = new char[logLength];
            glGetProgramInfoLog(program, logLength, &logLength, log);
            LOGE("Program link error: %s", log);
            delete[] log;
        }
        release();
        return;
    }

    // 获取着色器中uniform和attribute变量的位置
    textureUniform = glGetUniformLocation(program, "texture");
    positionAttribute = glGetAttribLocation(program, "aPosition");
    texCoordAttribute = glGetAttribLocation(program, "aTexCoord");

    // 创建并绑定 VAO
    glGenVertexArrays(1, &vao);
    glBindVertexArray(vao);

    // 创建并绑定 VBO
    glGenBuffers(1, &vbo);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertexVertices) + sizeof(texVertices), nullptr,
                 GL_STATIC_DRAW);
    glBufferSubData(GL_ARRAY_BUFFER, 0, sizeof(vertexVertices), vertexVertices);
    glBufferSubData(GL_ARRAY_BUFFER, sizeof(vertexVertices), sizeof(texVertices), texVertices);

    // 设置顶点位置和纹理坐标属性指针
    glVertexAttribPointer(positionAttribute, 3, GL_FLOAT, GL_FALSE, 0, nullptr);
    glEnableVertexAttribArray(positionAttribute);
    glVertexAttribPointer(texCoordAttribute, 2, GL_FLOAT, GL_FALSE, 0,
                          (void *) sizeof(vertexVertices));
    glEnableVertexAttribArray(texCoordAttribute);

    // 解绑 VAO
    glBindVertexArray(0);
}

void OpenGLRender::unInit() {
    // 释放资源
    release();
}

void OpenGLRender::renderFrame() {
    // 计时开始
    struct timeval start, end;

    gettimeofday(&start, NULL);

    // 渲染
    glViewport(0, 0, width, height);
    glUseProgram(program);
    glActiveTexture(GL_TEXTURE0);
    glUniform1i(textureUniform, 0);

    // 绑定 VAO
    glBindVertexArray(vao);

    // 渲染
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    // 解绑 VAO
    glBindVertexArray(0);

    eglSwapBuffers(display, windowSurface);

    // 计时结束
    gettimeofday(&end, NULL);
    long seconds = end.tv_sec - start.tv_sec;
    long microseconds = end.tv_usec - start.tv_usec;
    long milleseconds = seconds * 1000 + microseconds / 1000;
    LOGD("xzc Render time: %ld ms", milleseconds);
}

void OpenGLRender::release() {
    // 释放EGL资源
    if (display != EGL_NO_DISPLAY) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (windowSurface != EGL_NO_SURFACE) {
            eglDestroySurface(display, windowSurface);
            windowSurface = EGL_NO_SURFACE;
        }
        if (context != EGL_NO_CONTEXT) {
            eglDestroyContext(display, context); // Destroy EGL context
            context = EGL_NO_CONTEXT;
        }
        eglTerminate(display);
        display = EGL_NO_DISPLAY;
    }

    // 删除 VAO 和 VBO
    glDeleteVertexArrays(1, &vao);
    glDeleteBuffers(1, &vbo);

    // 删除着色器程序
    glDeleteProgram(program);
}
