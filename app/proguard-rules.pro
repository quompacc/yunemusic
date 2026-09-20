# NewPipe's locale pattern classes retain their names for versions that load
# them dynamically. Do not retain the entire extractor.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

# Rhino initializes builtins and loads runtime classes through reflection.
# Preserve this library boundary, as in NewPipe's upstream rules:
# https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro
# All other code remains eligible for optimization and obfuscation.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }

# Optional Java SE scripting integration is unavailable on Android.
# NewPipe invokes Rhino Context directly in interpreted mode instead.
-dontwarn javax.script.**
# Rhino's JVM bytecode optimizer uses Dynalink; Android extraction explicitly
# selects the interpreter. Java bean -> JSON conversion is likewise unused.
# Match NewPipe's Android rules, without hiding warnings for all of Rhino.
-dontwarn jdk.dynalink.**
-dontwarn org.mozilla.javascript.JavaToJSONConverters

# Room, Hilt, OkHttp, Media3 and kotlinx.serialization supply consumer rules.
# No blanket keeps for app models/DAOs or global -dontoptimize are necessary.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
