# MISSXXX / MISSPipe low-spec APK build

This project keeps PipePipe's Android structure:

- App root: `PipePipe-main/PipePipe-main/PipePipeClient`
- Extractor included build: `PipePipe-main/PipePipe-main/PipePipeExtractor`
- missAV reference API: `missAV_api-main/missAV_api-main`

## One-time setup

Install:

- Android Studio or Android command line tools
- Android SDK Platform 37
- Java 21. Android Studio bundled JBR works.

Create `PipePipe-main/PipePipe-main/PipePipeClient/local.properties`:

```properties
sdk.dir=C\:\\Users\\owner\\AppData\\Local\\Android\\Sdk
```

Change the path if your SDK is installed elsewhere.

## Low-memory debug APK command

Run from `PipePipe-main/PipePipe-main/PipePipeClient`:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_OPTS="-Xmx1536m -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process"
.\gradlew.bat assembleDebug --no-daemon --max-workers=1 --no-configuration-cache
```

Output APK path:

```text
PipePipe-main/PipePipe-main/PipePipeClient/app/build/outputs/apk/debug/
```

## Lower memory fallback

If the PC has very little RAM, try:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_OPTS="-Xmx1024m -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process"
.\gradlew.bat assembleDebug --no-daemon --max-workers=1 --no-configuration-cache
```

## Release APK command

Unsigned/release configuration still follows the inherited PipePipe signing setup.
If signing env vars are configured, run:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_OPTS="-Xmx1536m -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process"
.\gradlew.bat assembleRelease --no-daemon --max-workers=1 --no-configuration-cache
```

## Notes

- Do not run parallel Gradle builds on a low-spec PC.
- Close Android Studio before CLI builds if memory is tight.
- The source tree is set up to use the local included `PipePipeExtractor`, so extractor changes are picked up by the app build.
