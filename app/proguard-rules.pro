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
# OpenTelemetry 1.43's optional JSON serializer uses Jackson. Android creates
# only the explicit OTLP/HTTP protobuf exporters in OtelBootstrap.
-dontwarn com.fasterxml.jackson.core.JsonFactory
-dontwarn com.fasterxml.jackson.core.JsonGenerator

# Apache SSHD 2.20's ExceptionUtils uses JMX wrappers only behind !OsUtils.isAndroid().
# Android has neither wrapper. Preserve strict missing-class checks for other types.
-dontwarn javax.management.MBeanException
-dontwarn javax.management.ReflectionException
