# Regras do R8 específicas do app.
# As regras padrão vêm de proguard-android-optimize.txt (veja app/build.gradle.kts).

# youtubedl-android (yt-dlp/ffmpeg): a biblioteca não publica regras próprias e usa o
# Jackson por reflexão (mapeamento das respostas e verificação de atualização).
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.io.**

# libarchive chama as classes Java pelo nome (JNI).
-keep class me.zhanghai.android.libarchive.** { *; }
# Compressores opcionais do Commons Compress que não embarcamos.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.apache.commons.compress.**
