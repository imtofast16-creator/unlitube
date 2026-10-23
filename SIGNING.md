# UnliTube signing, read this before the first public APK

Android updates only install over an app signed with the SAME key. If the key
changes, every user must uninstall first. Protect this key like a password.

## One time setup, done by Joel on his own PC
1. Install Android Studio, or any JDK 17.
2. Generate the release keystore (keep the passwords in a password manager):

   keytool -genkeypair -v -keystore unlitube-release.jks -alias unlitube -keyalg RSA -keysize 2048 -validity 10000

3. Back up unlitube-release.jks in TWO places that are not this repo
   (for example an encrypted drive and a password manager attachment).
   Never commit the .jks file to GitHub.
4. In the GitHub repo, add these Actions secrets:
   - UNLITUBE_KEYSTORE_B64: base64 of the .jks file (base64 -w0 unlitube-release.jks)
   - UNLITUBE_KEYSTORE_PASSWORD
   - UNLITUBE_KEY_ALIAS (unlitube)
   - UNLITUBE_KEY_PASSWORD

## Wiring
Already wired: app/build.gradle.kts reads UNLITUBE_KEYSTORE_PATH and the
password and alias variables from the environment. The CI workflow decodes
the base64 secret to a temp file, sets those variables, and builds a signed
assembleRelease. Debug builds need no key and work today.
