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
