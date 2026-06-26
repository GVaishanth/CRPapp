# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line number information for crash debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Protocol Buffers / Proto DataStore rules
-keep class com.example.crashresilientpdf.core.checkpoint.proto.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# PDFView (Barteksc / Mhiew) rules
-keep class com.github.barteksc.pdfviewer.** { *; }
-keep class com.github.barteksc.pdfviewer.scroll.** { *; }
-dontwarn com.github.barteksc.pdfviewer.**

# Coroutines & ViewModel rules
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.lifecycle.** { *; }

# Preserve core resilience manager contracts
-keep class com.example.crashresilientpdf.core.anomaly.** { *; }
-keep class com.example.crashresilientpdf.core.checkpoint.** { *; }
-keep class com.example.crashresilientpdf.core.recovery.** { *; }
