# v0.1
- Initial release and rebranding.

# v0.1-beta
- Replaced the signing keystore with a new dedicated `requi.jks` (alias `requi`); signing now reads from `keystore.properties` locally and from GitHub Secrets on CI.
- CI: bumped JDK 17 to 21 because AGP 9.2.1 requires JDK 21+, added `concurrency`, per-job timeouts, and `gradle/actions/setup-gradle` caching.
- CI: releases are now published only on version tags (`v*`) instead of every push; the GitHub release body is generated from `changelog.md`.
- CI: only the signed Release APK is attached to GitHub releases (the Debug APK is no longer published).
- Build: raised the Gradle JVM heap (`org.gradle.jvmargs`) to avoid `OutOfMemoryError` during R8 minification.
- Removed the previously committed keystore from the repository (moved to GitHub Secrets).
