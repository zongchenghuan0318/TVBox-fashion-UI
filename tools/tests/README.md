# Local JVM regressions

Online update regressions:

```powershell
./tools/tests/run-update.ps1
```

This compiles the current production `UpdateClient.java` and runs its JUnit
tests using the project's cached dependencies. It avoids the older Gradle
test worker's class-loading failure in Unicode Windows paths. It covers
numeric version comparisons, invalid manifests, route fallback, timeouts,
HTML responses, truncated/corrupt downloads, package-verifier rejection,
and cancellation without fallback or leftover files. No actual network or
installation is performed by these tests. First resolve the existing Gradle
test dependencies if JUnit is not cached.

Run from the project root:

```powershell
./tools/tests/run-controller-ticker.ps1
```

The script uses the JDK configured in `gradle.properties`, SDK in
`local.properties`, existing debug player classes, and the cached AndroidX
annotation jar. Optional overrides: `-JavaHome` and `-AndroidSdkRoot`.
No dependency is added or downloaded. A prior player debug compilation is required.

The harness compiles the **current production `BaseVideoController.java`** into
`player/build/controller-ticker-regression`, then executes its actual
`mShowProgress` anonymous Runnable and `startProgress`/`stopProgress` methods.
Unsafe only bypasses Android View construction; a deterministic queue replaces
View posting, and a media-control proxy supplies duration/position/state. It does
not reproduce the ticker algorithm in test code.

Six groups cover playing, paused seeking, buffering/resume, stopping from inside
the progress callback, stopping/restarting from that callback without duplicate
scheduled work, and terminal error/completed/idle states. The stop/restart case
also checks 20 subsequent dispatches for queue growth.

This is a JVM scheduling regression, not a substitute for on-device playback,
layout, native subtitle decoding, or Android lifecycle instrumentation. JDK 11
prints expected warnings for the test-only Unsafe API and Java 8 source target.
