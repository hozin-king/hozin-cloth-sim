# JNI bridge harus utuh (nama method native dipanggil dari C++).
-keep class com.hozinking.clothsim.ClothEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
