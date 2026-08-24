# JNI entry points must survive shrinking
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class app.modem73.NativeCore { *; }
