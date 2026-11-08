# LAME (Java port) — third-party source

The MP3 encoder used by this app is a pure-Java port of LAME 3.98.4 ("jump3r" / "java-lame").

- Origin: https://github.com/nwaldispuehl/java-lame (commit 0383c5d82e5f3d0acab347498743a74e9e69d256)
- Original project: LAME, https://lame.sourceforge.io/
- License: GNU Lesser General Public License (see LICENSE in this folder)

Copied unchanged into `app/src/main/java/net/sourceforge/lame/` (packages `mp3` and `mpg`).
Not included: `Main.java` (command-line front end) and the `lowlevel` package, which depends on
`javax.sound` and is unavailable on Android. The app talks to the encoder through
`app/src/main/java/com/aismart/ringtone/Mp3Encoder.java`.

Because the full source is part of this repository, the library can be modified or replaced and the
app rebuilt, as the LGPL requires.
