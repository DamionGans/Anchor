# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable

# Preserve BuildConfig fields used for version checking
-keep class com.damiongans.anchor.BuildConfig { *; }

# Preserve AndroidViewModel constructor used by ViewModelProvider reflection
-keep class com.damiongans.anchor.viewmodel.MetronomeViewModel {
    public <init>(android.app.Application);
}
