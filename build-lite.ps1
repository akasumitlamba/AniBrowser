param([switch]$TestsOnly)
$ErrorActionPreference = 'Stop'
$liteRoot = Join-Path $PSScriptRoot 'lite'
$sdkRoot = Join-Path $PSScriptRoot '.tools/sdk'
if (!(Test-Path -LiteralPath (Join-Path $liteRoot 'local.properties'))) {
    ('sdk.dir=' + $sdkRoot.Replace('\','/').Replace(':','\:')) | Set-Content -LiteralPath (Join-Path $liteRoot 'local.properties')
}
& node --test (Join-Path $liteRoot 'app/src/test/playback.test.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Lite playback tests failed.' }
$liteGradle = Join-Path $PSScriptRoot '.tools/gradle-9.7.1/bin/gradle.bat'
$liteTasks = @(':app:testDebugUnitTest', ':app:lintDebug')
if (!$TestsOnly) { $liteTasks += ':app:assembleDebug' }
& $liteGradle -p $liteRoot @liteTasks --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Lite build/check failed.' }
if (!$TestsOnly) {
    $liteOutput = Join-Path $PSScriptRoot 'output/lite'
    New-Item -ItemType Directory -Path $liteOutput -Force | Out-Null
    Get-ChildItem (Join-Path $liteRoot 'app/build/outputs/apk/debug') -Filter '*.apk' | ForEach-Object {
        $destination = Join-Path $liteOutput ($_.Name.Replace('app-','AniBrowser-Lite-0.3.0-'))
        Copy-Item -LiteralPath $_.FullName -Destination $destination
        Get-FileHash -LiteralPath $destination -Algorithm SHA256
    }
}
