# Retrofit annotations and interfaces
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Kotlin coroutine continuation generic signature
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Serialization-related metadata
-keepattributes *Annotation*
