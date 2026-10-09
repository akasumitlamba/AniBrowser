param([switch]$TestsOnly)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $project '.tools\sdk'
$androidJar = Join-Path $sdk 'platforms\android-37.1\android.jar'
$buildTools = Join-Path $sdk 'build-tools\37.0.0'
Set-Location $project
function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $exe (exit $LASTEXITCODE)" }
}
New-Item -ItemType Directory -Force -Path brave\compiled\app-classes,brave\compiled\test-classes,brave\compiled\patches,brave\compiled\dex,brave\downloads,brave\tools,brave\signing,output\anibrave | Out-Null
Run 'node' @('--test','brave/tests/playback.test.cjs')
Run 'javac' @('-d','brave/compiled/test-classes','brave/src/SiteKey.java','brave/tests/SiteKeyTest.java')
Run 'java' @('-cp','brave/compiled/test-classes','app.anibrave.SiteKeyTest')
Run 'javac' @('-d','brave/compiled/test-classes','brave/src/EngineHttp.java','brave/tests/EngineHttpTest.java')
Run 'java' @('-cp','brave/compiled/test-classes','app.anibrave.EngineHttpTest')
if ($TestsOnly) { return }
if (!(Test-Path -LiteralPath $androidJar)) { throw 'Android SDK 37.1 is required in .tools/sdk' }
$apk = 'brave/downloads/Bravearm64Universal.apk'
if (!(Test-Path -LiteralPath $apk)) {
    Run 'gh' @('release','download','v1.97.56','--repo','brave/brave-browser','--pattern','Bravearm64Universal.apk','--dir','brave/downloads')
}
if ((Get-FileHash $apk).Hash -ne '9B02840FB94DC172D3BF0073CE6877372BEA0E311BABA7952E4A0563DF88AE89') { throw 'Upstream Brave APK checksum mismatch' }
$jars = @{
    'apktool.jar'='https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar'
    'dexlib2.jar'='https://repo.maven.apache.org/maven2/org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar'
    'smali.jar'='https://repo.maven.apache.org/maven2/org/smali/smali/2.5.2/smali-2.5.2.jar'
    'smali-util.jar'='https://repo.maven.apache.org/maven2/org/smali/util/2.5.2/util-2.5.2.jar'
    'guava.jar'='https://repo.maven.apache.org/maven2/com/google/guava/guava/27.1-android/guava-27.1-android.jar'
    'antlr-runtime.jar'='https://repo.maven.apache.org/maven2/org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar'
    'antlr.jar'='https://repo.maven.apache.org/maven2/org/antlr/antlr/3.5.2/antlr-3.5.2.jar'
    'jcommander.jar'='https://repo.maven.apache.org/maven2/com/beust/jcommander/1.64/jcommander-1.64.jar'
}
foreach ($jar in $jars.GetEnumerator()) {
    $path = Join-Path 'brave/tools' $jar.Key
    if (!(Test-Path -LiteralPath $path)) { Invoke-WebRequest -Uri $jar.Value -OutFile $path }
}
$lock = Get-Content brave/tools/tool-checksums.json -Raw | ConvertFrom-Json
foreach ($entry in $lock.PSObject.Properties) {
    if ((Get-FileHash ('brave/tools/'+$entry.Name)).Hash.ToLowerInvariant() -ne $entry.Value) { throw "Tool checksum mismatch: $($entry.Name)" }
}
$tool = 'brave/tools/apktool.jar'
Run 'java' @('-jar',$tool,'install-framework',$androidJar,'-p','brave/tools/framework')
if (!(Test-Path brave/decoded/smali_classes5/ukf.smali)) { Run 'java' @('-jar',$tool,'decode',$apk,'--output','brave/decoded','--no-res','--jobs','4','-p','brave/tools/framework') }
if (!(Test-Path brave/resources/AndroidManifest.xml)) { Run 'java' @('-jar',$tool,'decode',$apk,'--output','brave/resources','--no-src','--jobs','4','-p','brave/tools/framework') }
Run 'python' @('brave/tools/prepare.py')
$javaSources = @(Get-ChildItem brave/src/*.java | ForEach-Object FullName)
Run 'javac' (@('-encoding','UTF-8','--release','17','-cp',$androidJar,'-d','brave/compiled/app-classes')+$javaSources)
Run 'jar' @('--create','--file','brave/compiled/anibrave.jar','-C','brave/compiled/app-classes','.')
Run (Join-Path $buildTools 'd8.bat') @('--min-api','29','--lib',$androidJar,'--output','brave/compiled','brave/compiled/anibrave.jar')
$mergeCp = 'brave/compiled;brave/tools/dexlib2.jar;brave/tools/guava.jar'
Run 'javac' @('-cp','brave/tools/dexlib2.jar;brave/tools/guava.jar','-d','brave/compiled','brave/tools/MergeDex.java')
$smaliCp = 'brave/tools/smali.jar;brave/tools/dexlib2.jar;brave/tools/smali-util.jar;brave/tools/guava.jar;brave/tools/antlr-runtime.jar;brave/tools/antlr.jar;brave/tools/jcommander.jar'
Run 'java' @('-cp',$smaliCp,'org.jf.smali.Main','assemble','--api','29','--output','brave/compiled/hooks.dex','brave/compiled/patches')
foreach ($name in @('classes.dex','classes2.dex','classes3.dex','classes4.dex','classes5.dex','classes6.dex')) {
    $mergeArgs = @('-Xmx2g','-cp',$mergeCp,'MergeDex',('brave/resources/'+$name),('brave/compiled/dex/'+$name))
    if ($name -eq 'classes5.dex') { $mergeArgs += 'brave/compiled/hooks.dex' }
    Run 'java' $mergeArgs
}
Run 'java' @('-Xmx2g','-cp',$mergeCp,'MergeDex','brave/compiled/hooks.dex','brave/compiled/dex/classes7.dex')
Copy-Item -LiteralPath brave/compiled/classes.dex -Destination brave/compiled/dex/classes8.dex
Run 'java' @('-jar',$tool,'build','brave/resources','--output','brave/compiled/resources.apk','--jobs','4','-p','brave/tools/framework')
Run 'python' @('brave/tools/package.py')
$key = 'brave/signing/anibrave.jks'
if (!(Test-Path -LiteralPath $key)) { Run 'keytool' @('-genkeypair','-keystore',$key,'-storepass','anibrave-local-build','-keypass','anibrave-local-build','-alias','anibrave','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=AniBrave Personal Build') }
Run (Join-Path $buildTools 'zipalign.exe') @('-f','-P','16','4','brave/compiled/AniBrave-unsigned.apk','brave/compiled/AniBrave-aligned.apk')
$result = 'output/anibrave/AniBrave-0.1.0-arm64-v8a.apk'
Run (Join-Path $buildTools 'apksigner.bat') @('sign','--ks',$key,'--ks-key-alias','anibrave','--ks-pass','pass:anibrave-local-build','--key-pass','pass:anibrave-local-build','--out',$result,'brave/compiled/AniBrave-aligned.apk')
Run (Join-Path $buildTools 'apksigner.bat') @('verify','--verbose','--print-certs',$result)
Run (Join-Path $buildTools 'zipalign.exe') @('-c','-P','16','4',$result)
Run 'python' @('brave/tools/verify.py')
Write-Host "Built $result"
