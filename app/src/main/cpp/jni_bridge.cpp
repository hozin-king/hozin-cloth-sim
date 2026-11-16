// JNI bridge: ClothEngine (Kotlin) <-> cloth::ClothSim (C++).
// Satu handle (pointer) per instance; Kotlin yang memiliki lifecycle.
// Posisi + normal diekspos sebagai DirectByteBuffer (tanpa salinan per frame);
// buffer menjadi tidak valid setelah recreate — Kotlin mengambil ulang.
#include <jni.h>

#include "cloth.h"

using cloth::ClothSim;

static ClothSim* toSim(jlong h) { return reinterpret_cast<ClothSim*>(h); }

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeCreate(JNIEnv*, jobject, jint segments) {
    return reinterpret_cast<jlong>(new ClothSim(segments));
}

JNIEXPORT void JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeDestroy(JNIEnv*, jobject, jlong h) {
    delete toSim(h);
}

JNIEXPORT void JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeReset(JNIEnv*, jobject, jlong h) {
    toSim(h)->reset();
}

JNIEXPORT void JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeStep(JNIEnv*, jobject, jlong h, jfloat dt,
                                                   jfloat wind, jfloat time) {
    toSim(h)->step(dt, wind, time);
}

JNIEXPORT jobject JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativePositions(JNIEnv* env, jobject, jlong h) {
    ClothSim* s = toSim(h);
    return env->NewDirectByteBuffer(const_cast<float*>(s->positions()),
                                    s->particleCount() * 3 * sizeof(float));
}

JNIEXPORT jobject JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeNormals(JNIEnv* env, jobject, jlong h) {
    ClothSim* s = toSim(h);
    return env->NewDirectByteBuffer(const_cast<float*>(s->normals()),
                                    s->particleCount() * 3 * sizeof(float));
}

JNIEXPORT jobject JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeIndices(JNIEnv* env, jobject, jlong h) {
    ClothSim* s = toSim(h);
    return env->NewDirectByteBuffer(const_cast<uint32_t*>(s->indices()),
                                    s->indexCount() * sizeof(uint32_t));
}

JNIEXPORT jint JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeParticleCount(JNIEnv*, jobject, jlong h) {
    return toSim(h)->particleCount();
}

JNIEXPORT jint JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeIndexCount(JNIEnv*, jobject, jlong h) {
    return toSim(h)->indexCount();
}

JNIEXPORT jint JNICALL
Java_com_hozinking_clothsim_ClothEngine_nativeIterations(JNIEnv*, jobject, jlong h) {
    return toSim(h)->iterations();
}

}  // extern "C"
