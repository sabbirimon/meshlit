# Empty consumer ProGuard rules for :core-stable-diffusion.
# Phase 4.x — when stable-diffusion.cpp + ggml land in Phase 2, add
# -keepclasseswithmembernames for the JNI surface here so R8 doesn't
# strip the native method signatures. For MVP1 the stub body is too
# small for R8 to bite; nothing to do yet.
