# Third-party notices

This project uses the following third-party libraries, all licensed under the Apache License 2.0:

- AndroidX (Core, Activity, Lifecycle) and Jetpack Compose (Material 3)
- AndroidX Media3 (ExoPlayer, Transformer, Common)
- Kotlin and kotlinx.coroutines

Review their respective licenses and notices before distributing a release build.

No FFmpeg binaries are bundled in this project.

The links to Pixabay, Jamendo and Free Music Archive are provided only for convenience.
This project is not affiliated with those services. Each track has its own license; check it before use.

## LAME MP3 encoder (java-lame port)

MP3 export uses a Java port of the LAME encoder (java-lame 3.98.4), licensed under the
GNU LGPL. The source is vendored in `app/src/main/java/net/sourceforge/lame/`, and the
license text and notes are in `third_party/lame/`. You may modify and rebuild it under the LGPL terms.
