# Anands Launcher

Home screen for Anands OS. Transparent over your wallpaper, orange/black/grey.

- Home: greeting, search, your web apps (edit the `FAVORITES` list at the top of `HomeActivity.kt`), dock with phone, messages, camera and all apps.
- Drawer: swipe up or tap the orange button. Type to filter, press Go to open the first match. Long-press an app for App info or Uninstall.
- Back never leaves the launcher.

## Get the APK (about 5 minutes)

1. Create an empty private repo on GitHub, for example `anands-launcher`.
2. In this folder:
   ```
   git init
   git add .
   git commit -m "Anands Launcher"
   git branch -M main
   git remote add origin https://github.com/<your-user>/anands-launcher.git
   git push -u origin main
   ```
3. On GitHub open the Actions tab, wait for "Build APK" to go green, open the run and download the `anands-launcher-apk` artifact. Unzip it to get `app-debug.apk`.

## Install on the phone

```
adb install -r app-debug.apk
```

Press the Home button, choose Anands Launcher and tap Always. To switch back: Settings > Apps > Choose default apps > Home app.

## If the build fails

Open the failed run, copy the red error lines and send them to Claude.
