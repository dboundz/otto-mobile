# Android app (nested copy)

This folder is a **nested Gradle project** inside the iOS repo workspace. Day-to-day Android development and CI should use the **canonical** tree:

**`../../otto-android`** (sibling of `otto-mobile` under `mobile/`, i.e. `mobile/otto-android`)

When you open Android Studio from `otto-mobile/otto-android`, you are building **this** copy. Drive camera, map, and shell changes must be kept in sync with `mobile/otto-android` or you will not see fixes on device.

After pulling drive/map changes, rebuild and reinstall:

```bash
cd ../../otto-android   # preferred
./gradlew :app:installDebug
```

Or from this folder:

```bash
./gradlew :app:installDebug
```
