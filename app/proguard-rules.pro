# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================
# Reglas de Kickoff AR para el build de release (minificado)
# ============================================================

# Stack traces legibles si algún usuario reporta un crash
-keepattributes SourceFile,LineNumberTable

# --- Gson + DTOs ---
# R8 renombra/elimina campos que "nadie usa"... pero Gson los
# llena por reflexión en runtime, así que para R8 parecen
# muertos. Sin estas reglas, el JSON llega y los objetos salen
# con todo en null (la app "funciona" pero sin datos).
-keepattributes Signature, *Annotation*
-keep class com.futbolarg.futbolargentinowidgets.data.remote.dto.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken