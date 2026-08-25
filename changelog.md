# Unreleased
- UX: pressing Back during multi-select now clears the selection instead of closing the app.
- UX: empty state now distinguishes "no recordings at all" from "no filter matches", with a one-tap "Clear All Filters" action in the latter.
- UX: multi-select delete now runs sequentially with a progress dialog (N of M, cancellable) instead of firing all deletions at once with no feedback.
- Performance: search input is debounced and the filter pipeline runs off the main thread (`Dispatchers.Default`).
- Performance: recycle-bin operations (list, permanent delete, empty bin) no longer perform disk I/O on the main thread.
- Performance: per-row date formatting is computed once and cached, removing repeated `SimpleDateFormat` construction while scrolling.

# v0.1
- Initial release and rebranding.

# v0.1-beta
- Replaced the signing keystore with a new dedicated `requi.jks` (alias `requi`); signing now reads from `keystore.properties` locally and from GitHub Secrets on CI.
- CI: bumped JDK 17 to 21 because AGP 9.2.1 requires JDK 21+, added `concurrency`, per-job timeouts, and `gradle/actions/setup-gradle` caching.
- CI: releases are now published only on version tags (`v*`) instead of every push; the GitHub release body is generated from `changelog.md`.
- CI: only the signed Release APK is attached to GitHub releases (the Debug APK is no longer published).
- Build: raised the Gradle JVM heap (`org.gradle.jvmargs`) to avoid `OutOfMemoryError` during R8 minification.
- Removed the previously committed keystore from the repository (moved to GitHub Secrets).
