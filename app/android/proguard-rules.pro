# LiteRT-LM uses JNI and reflection (tool annotations); keep its classes if minify is turned on.
-keep class com.google.ai.edge.litertlm.** { *; }
