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

# HeliBoard JNI bridge classes are registered from native code by exact class and
# method names. Do not shrink or obfuscate them in SyncMesh release builds.
-keep class com.android.inputmethod.latin.BinaryDictionary { *; }
-keep class com.android.inputmethod.latin.DicTraverseSession { *; }
-keep class com.android.inputmethod.latin.utils.BinaryDictionaryUtils { *; }
-keep class com.android.inputmethod.keyboard.ProximityInfo { *; }

-keep class com.android.inputmethod.latin.utils.WordInputEventForPersonalization { *; }
-keep class helium314.keyboard.latin.common.NativeSuggestOptions { *; }
-keep class helium314.keyboard.latin.dictionary.Dictionary { *; }
-keep class helium314.keyboard.latin.NgramContext { *; }
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

# --- SyncMesh keyboard bridge (reflection) ---
# The bundled keyboard (helium314.keyboard.latin.syncmesh.SyncMeshBridge) calls into these
# app classes/members BY NAME via reflection. R8 cannot see those references, so without these
# keep rules it may strip or rename them in minified release builds, silently breaking keyboard
# auto-send and the other bridge entry points. Keep the exact reflected surface.
-keep class com.ankit.syncmesh.sync.SyncCoordinator {
    public static com.ankit.syncmesh.sync.SyncCoordinator getInstance(android.content.Context);
    public void sendManualClipboardText(java.lang.String, com.ankit.syncmesh.sync.SyncCoordinator$ActionCallback);
}
-keep interface com.ankit.syncmesh.sync.SyncCoordinator$ActionCallback { *; }
-keep class com.ankit.syncmesh.data.AppRepository {
    public static com.ankit.syncmesh.data.AppRepository getInstance(android.content.Context);
    public com.ankit.syncmesh.data.AppPreferences getPreferences();
    public java.util.ArrayList getClipboardHistory();
    public java.lang.String getLatestRemoteClipboardText();
}
-keep class com.ankit.syncmesh.data.AppPreferences {
    public boolean isKeyboardAutoSendEnabled();
    public void setKeyboardAutoSendEnabled(boolean);
}
# Model fields are read reflectively by SyncMeshBridge.getClipboardHistory(...).
-keepclassmembers class com.ankit.syncmesh.model.ClipboardModel {
    public <fields>;
}
-keep class com.ankit.syncmesh.model.ClipboardEntry
