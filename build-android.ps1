param([switch]$TestsOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Push-Location (Join-Path $projectRoot 'android')
try {
    $controllerTests = @(Get-ChildItem app/src/test -Filter '*.test.cjs' | Sort-Object Name | ForEach-Object FullName)
    & node --test @controllerTests
    if ($LASTEXITCODE -ne 0) { throw 'Controller tests failed.' }
    $localGradle = Join-Path $projectRoot '.tools/gradle-9.7.1/bin/gradle.bat'
    $buildCommand = if (Test-Path -LiteralPath $localGradle) { $localGradle } else { '.\gradlew.bat' }
    if ($TestsOnly) {
        & $buildCommand :app:testDebugUnitTest --console=plain '-PcentralRepo=https://maven-central.storage-download.googleapis.com/maven2'
        if ($LASTEXITCODE -ne 0) { throw 'Android unit tests failed.' }
        return
    }
    & $buildCommand :app:testDebugUnitTest :app:assembleDebug --console=plain '-PcentralRepo=https://maven-central.storage-download.googleapis.com/maven2'
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed. No new APK is verified.' }
    Get-ChildItem app/build/outputs/apk -Recurse -Filter '*.apk' | ForEach-Object {
        Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
    }
} finally {
    Pop-Location
}
