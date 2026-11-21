# Irodori Study — Android App Project

A lightweight Android WebView wrapper for the **IRODORI Japanese Online Course** (`https://www.irodori-online.jpf.go.jp/`) with built-in study tools tailored for **JFT-Basic / JLPT N4 / SSW** learners.

## Included Features
- **Full Irodori Learning Website in a Clean App**: Persistent cookies & `localStorage` (stay logged in), fullscreen video playback, and microphone permission support for speaking exercises.
- **Auto-Resume & Lesson Bookmarks**: Reopens your last visited lesson automatically and lets you bookmark specific lessons.
- **Vocab, Kanji & Grammar Scratchpad**: Slide-up notes sheet saved locally on your phone with one-tap export/share.
- **Quick Jisho.org Lookup**: Look up unfamiliar words or kanji without losing your lesson page.
- **Pomodoro Focus Timer + Daily Streak Tracker**: Tracks daily study minutes and consecutive study days right in the top bar.
- **Keep Screen Awake Toggle**: Stops your screen from dimming during listening and reading activities.
- **Quick Switch to Official IRODORI Practice App**: One tap in the Tools sheet opens the Japan Foundation's `IRODORI Practice` app (or its Play Store page).

---

## How to Build the `.apk` File

### Option 1: Build Free in the Cloud (No PC or Android Studio Needed)
1. Create a free repository on [GitHub](https://github.com/new).
2. Unzip `IrodoriStudyApp-Android.zip` and upload all files (including the `.github/workflows/build-apk.yml` file) to your repository.
3. Open the **Actions** tab in your GitHub repository and select **Build Android APK** (it starts automatically on push, or click **Run workflow**).
4. Wait ~2–3 minutes for the build to finish, then download **Irodori-Study-APK** from the **Artifacts** section.
5. Unzip the downloaded artifact and install `app-debug.apk` on your Android phone (enable *Install unknown apps* if prompted).

### Option 2: Build Locally with Android Studio
1. Unzip `IrodoriStudyApp-Android.zip`.
2. Open the `IrodoriStudyApp` folder in **Android Studio** (Ladybug or newer).
3. Wait for Gradle Sync to finish.
4. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Copy `app/build/outputs/apk/debug/app-debug.apk` to your Android phone and install it.
