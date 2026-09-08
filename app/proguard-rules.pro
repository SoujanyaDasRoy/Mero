# Release builds strip reflectively-reached code. See .claude/skills/ship-release.
# Keep rules for Media3, Ktor and kotlinx-serialization get added when those
# dependencies land (M1 Task 2 onward) — this file is intentionally empty until
# there is something real to keep.

# slf4j ships an API jar whose binder is supplied at runtime by an
# implementation we do not bundle (youtubedl-android pulls the API in
# transitively and never logs through it). R8 only needs to be told the
# absent binder is absent on purpose.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# JTransforms (equalizer spectrum) pulls in JLargeArrays, which reaches for
# sun.misc.Cleaner to free arrays larger than 2GB off-heap. That class is
# JVM-only and absent on Android, and the path is unreachable here — the FFT
# window is 1024 samples. Without this R8 fails the release build outright.
-dontwarn sun.misc.**
-dontwarn pl.edu.icm.jlargearrays.**
