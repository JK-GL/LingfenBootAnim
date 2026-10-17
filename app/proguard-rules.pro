# Compose 自带 consumer rules，这里只补必要的保留项
-keepattributes SourceFile,LineNumberTable
-keep class com.lingfen.bootanim.** { *; }

# Java 侧被 Kotlin 调用的核心类
-keep class com.lingfen.bootanim.FrameExtractor { *; }
-keep class com.lingfen.bootanim.BootPackager { *; }
-keep class com.lingfen.bootanim.StoredZipWriter { *; }
-keep class com.lingfen.bootanim.StoragePublisher { *; }

# 去掉日志
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
