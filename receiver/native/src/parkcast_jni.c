/*
 * ParkCast native receiver (S3): the JNI entry points of parkcast.receiver.NativeBridge.
 * docs/design/S3_RECEIVER.md, "JNI contract".
 *
 * nativeStart(stage, keyfile, address, width, height, fps) returns "ok <token>" or "fail <token>".
 * nativeStop(phase) runs one stop phase. Events go up through NativeBridge.onNative from the delivery thread.
 */
#include "parkcast.h"

#include <string.h>

#define RESULT_MAX 96

static jstring utf_or_empty(JNIEnv *env, jstring s, const char **out)
{
    *out = "";
    if (!s) return NULL;
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    if (c) *out = c;
    return c ? s : NULL;
}

static void release(JNIEnv *env, jstring s, const char *c)
{
    if (s) (*env)->ReleaseStringUTFChars(env, s, c);
}

__attribute__((visibility("default")))
JNIEXPORT jstring JNICALL Java_parkcast_receiver_NativeBridge_nativeStart(JNIEnv *env, jclass cls, jint stage,
                                                                          jstring keyfile, jstring address,
                                                                          jint width, jint height, jint fps)
{
    const char *key, *addr;
    jstring key_held = utf_or_empty(env, keyfile, &key);
    jstring addr_held = utf_or_empty(env, address, &addr);
    char result[RESULT_MAX];
    strcpy(result, "fail state");
    host_start((int)stage, key, addr, (int)width, (int)height, (int)fps, result, (int)sizeof result);
    release(env, key_held, key);
    release(env, addr_held, addr);
    return (*env)->NewStringUTF(env, result);
}

__attribute__((visibility("default")))
JNIEXPORT void JNICALL Java_parkcast_receiver_NativeBridge_nativeStop(JNIEnv *env, jclass cls, jint phase)
{
    host_stop((int)phase);
}

__attribute__((visibility("default")))
JNIEXPORT jint JNICALL Java_parkcast_receiver_NativeBridge_nativeGeneration(JNIEnv *env, jclass cls)
{
    return (jint)ring_generation();
}

__attribute__((visibility("default")))
JNIEXPORT jboolean JNICALL Java_parkcast_receiver_NativeBridge_nativeAccepting(JNIEnv *env, jclass cls)
{
    return host_accepting() ? JNI_TRUE : JNI_FALSE;
}

__attribute__((visibility("default")))
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK || !env) return JNI_ERR;
    jclass local = (*env)->FindClass(env, "parkcast/receiver/NativeBridge");
    if (!local) return JNI_ERR;
    jmethodID mid = (*env)->GetStaticMethodID(env, local, "onNative", "(IIIIJLjava/lang/String;[B)V");
    if (!mid) return JNI_ERR;
    jclass global = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    if (!global || ring_set_target(vm, global, mid) != 0) return JNI_ERR;
    return JNI_VERSION_1_6;
}
