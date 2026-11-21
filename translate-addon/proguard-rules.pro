# JNI: liblumetranslate.so looks up BergamotNative's methods by their Java names.
-keep class com.lume.translate.addon.BergamotNative { native <methods>; }
