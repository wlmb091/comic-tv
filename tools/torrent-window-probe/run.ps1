param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Toolchain = (Join-Path $PSScriptRoot '../../../../work/toolchain')
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$toolchainPath=(Resolve-Path $Toolchain).Path
$cache=Join-Path $toolchainPath 'gradle-home/caches/modules-2/files-2.1'
function Jar([string]$path) {
    $result=Get-ChildItem -LiteralPath (Join-Path $cache $path) -Recurse -Filter '*.jar' | Select-Object -First 1
    if(!$result){throw "Dependência não encontrada: $path"}
    return $result.FullName
}
function Check([string]$step){if($LASTEXITCODE -ne 0){throw "Falha em $step (código $LASTEXITCODE)"}}
$oldJava=$env:JAVA_HOME
try {
    $env:JAVA_HOME=(Get-ChildItem (Join-Path $toolchainPath 'java') -Directory | Where-Object Name -Like 'jdk-17*' | Select-Object -First 1).FullName
    $sdk=Join-Path $toolchainPath 'android-sdk'
    $adb=Join-Path $sdk 'platform-tools/adb.exe'
    $android=Join-Path $sdk 'platforms/android-36/android.jar'
    $d8=Join-Path $sdk 'build-tools/35.0.0/d8.bat'
    $api=Jar 'org.libtorrent4j/libtorrent4j/2.1.0-38'
    $native=Jar 'org.libtorrent4j/libtorrent4j-android-arm/2.1.0-38'
    $stdlib=Jar 'org.jetbrains.kotlin/kotlin-stdlib/2.2.21'
    $coroutines=Jar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.10.2'
    $core=Join-Path $repo 'core/build/libs/core.jar'
    $classes=Join-Path $repo 'app/build/tmp/kotlin-classes/debug'
    if(!(Test-Path (Join-Path $classes 'com/pelican/comictv/tv/TvTorrentSession.class'))){throw 'Compile :app:compileDebugKotlin antes. Este script não gera APK.'}
    $id=[Guid]::NewGuid().ToString('N')
    $out=Join-Path (Split-Path $toolchainPath) "torrent-window-probe-$id"
    New-Item -ItemType Directory -Path $out | Out-Null
    & "$env:JAVA_HOME/bin/javac.exe" -source 8 -target 8 -classpath "$api;$stdlib;$coroutines;$android;$core;$classes" -d $out (Join-Path $PSScriptRoot 'TorrentProbe.java')
    Check 'javac'
    & python (Join-Path $PSScriptRoot 'make-fixture.py') $out $repo $native
    Check 'fixture'
    & $d8 --min-api 26 --lib $android --output (Join-Path $out 'probe-dex.zip') (Join-Path $out 'probe-input.jar') $core $api $stdlib $coroutines *> (Join-Path $out 'd8.log')
    Check 'D8'
    # Unique, validated Android scratch directory. Never touches the installed app's data.
    $remote="/data/local/tmp/comic-tv-torrent-probe-$id"
    if($remote -notmatch '^/data/local/tmp/comic-tv-torrent-probe-[0-9a-f]{32}$'){throw 'Diretório remoto inválido'}
    & $adb -s $Serial shell mkdir $remote
    Check 'mkdir remoto'
    & $adb -s $Serial push (Join-Path $out 'probe-dex.zip') (Join-Path $out 'libtorrent4j.so') (Join-Path $out 'synthetic.torrent') "$remote/"
    Check 'push'
    & $adb -s $Serial shell "CLASSPATH=$remote/probe-dex.zip app_process32 /system/bin TorrentProbe $remote" | Tee-Object -FilePath (Join-Path $out 'runtime.log')
    Check 'teste no projetor'
    if(!(Select-String -Path (Join-Path $out 'runtime.log') -SimpleMatch 'PASS native actual-session')){throw 'Teste não confirmou PASS; preservar a pasta remota para diagnóstico'}
    & $adb -s $Serial shell rm -r $remote
    Check 'limpeza da pasta temporária'
    Write-Output "Evidência local: $out"
} finally {$env:JAVA_HOME=$oldJava}
