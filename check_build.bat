@echo off
REM ════════════════════════════════════════════════════════════════════════
REM  REPLATE BUILD VERIFICATION SCRIPT
REM  This script checks if your Android project is ready to build
REM ════════════════════════════════════════════════════════════════════════

echo.
echo ╔═══════════════════════════════════════════════════════════════════════╗
echo ║                  REPLATE BUILD CHECKER                               ║
echo ║             Verifying Android Project Configuration                  ║
echo ╚═══════════════════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

REM Check 1: build.gradle exists
echo [1/8] Checking build.gradle...
if exist "app\build.gradle" (
    echo ✓ app\build.gradle found
) else (
    echo ✗ app\build.gradle NOT FOUND!
    goto ERROR
)

REM Check 2: google-services.json exists
echo [2/8] Checking google-services.json...
if exist "app\google-services.json" (
    echo ✓ app\google-services.json found
) else (
    echo ✗ app\google-services.json NOT FOUND!
    echo   Get this file from Firebase Console
    goto ERROR
)

REM Check 3: AndroidManifest.xml exists
echo [3/8] Checking AndroidManifest.xml...
if exist "app\src\main\AndroidManifest.xml" (
    echo ✓ app\src\main\AndroidManifest.xml found
) else (
    echo ✗ app\src\main\AndroidManifest.xml NOT FOUND!
    goto ERROR
)

REM Check 4: gradlew wrapper exists
echo [4/8] Checking Gradle wrapper...
if exist "gradlew" (
    echo ✓ gradlew wrapper found
) else (
    echo ✗ gradlew wrapper NOT FOUND!
    goto ERROR
)

REM Check 5: LocalPropertiesFile (SDK location)
echo [5/8] Checking local.properties...
if exist "local.properties" (
    echo ✓ local.properties found
) else (
    echo ⚠ local.properties NOT FOUND (will be auto-created)
)

REM Check 6: Testing files exist
echo [6/8] Checking source files...
if exist "app\src\main\java\com\replate\app\MainActivity.java" (
    echo ✓ MainActivity.java found
) else (
    echo ✗ MainActivity.java NOT FOUND!
    goto ERROR
)

REM Check 7: Resource files
echo [7/8] Checking resource files...
if exist "app\src\main\res\layout" (
    echo ✓ Resource layouts found
) else (
    echo ✗ Resource layouts NOT FOUND!
    goto ERROR
)

REM Check 8: Firebase configuration
echo [8/8] Checking Firebase configuration...
if exist "app\google-services.json" (
    echo ✓ Firebase configuration ready
) else (
    echo ✗ Firebase configuration missing!
    goto ERROR
)

echo.
echo ╔═══════════════════════════════════════════════════════════════════════╗
echo ║  ✅  ALL CHECKS PASSED! Project is ready to build!                    ║
echo ╚═══════════════════════════════════════════════════════════════════════╝
echo.
echo Next steps:
echo   1. Open Android Studio
echo   2. File → Open → Select REPLATE folder
echo   3. Wait for Gradle sync to complete
echo   4. Click "Build" → "Build Bundle(s)/APK(s)" → "Build APK(s)"
echo   5. Run on emulator: "Run" → "Run 'app'"
echo.
echo For detailed testing: Read APP_TESTING_GUIDE.md
echo.
pause
goto EOF

:ERROR
echo.
echo ╔═══════════════════════════════════════════════════════════════════════╗
echo ║  ✗  BUILD CHECK FAILED!                                              ║
echo ║  Please ensure all required files are present.                       ║
echo ║  Re-read the BUILD_COMPLETION_REPORT.md for details.                 ║
echo ╚═══════════════════════════════════════════════════════════════════════╝
echo.
pause
goto EOF

:EOF
exit /b

