![Rain Manager](banner.png)

# Rain Manager

A universal repository and Lsposed module android manager.  
Forked from [rushiranpise/lsposed-modules-manager](https://github.com/rushiranpise/lsposed-modules-manager).

## How to Build

### Prerequisites

- **JDK 17** or newer
- **Android SDK** (Platform 36 / 37 and Build-Tools 36.0.0)
- Set `ANDROID_HOME` to your Android SDK directory

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/Arch1vways/Rain-Manager.git
   cd Rain-Manager
   ```

2. Build the release APK:

   **Windows (PowerShell):**
   ```powershell
   $env:ANDROID_HOME = "C:\android-sdk"
   .\gradlew.bat assembleRelease
   ```

   **Linux / macOS:**
   ```bash
   export ANDROID_HOME="$HOME/Android/Sdk"
   ./gradlew assembleRelease
   ```

3. The built APK will be located at `app/build/outputs/apk/release/app-release.apk`.

## License

Licensed under the [MIT License](LICENSE).
