# Builds libcomictv_archive.so for the two ARM ABIs and copies it to app/src/main/jniLibs.
# Usage: .\native\archive\build.ps1 -Source <libarchive-3.8.11 folder> -Sdk <Android SDK folder>
# libarchive 3.8.11: https://github.com/libarchive/libarchive/releases/download/v3.8.11/libarchive-3.8.11.tar.gz
# SHA-256 900fe9fc7237c2b04f090f3c3648de8fe597525771f5035016537ce2d667d12b
param([Parameter(Mandatory)][string]$Source, [Parameter(Mandatory)][string]$Sdk, [string]$Ndk = '26.1.10909125')
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$app = Join-Path $here '..\..\app\src\main\jniLibs'
$cmake = Join-Path $Sdk 'cmake\3.22.1\bin\cmake.exe'
$ninja = Join-Path $Sdk 'cmake\3.22.1\bin\ninja.exe'
$toolchain = Join-Path $Sdk "ndk\$Ndk\build\cmake\android.toolchain.cmake"
$strip = Join-Path $Sdk "ndk\$Ndk\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe"
foreach ($abi in 'armeabi-v7a', 'arm64-v8a') {
    $build = Join-Path $env:TEMP "comictv-archive-$abi"
    if (Test-Path $build) { Remove-Item -Recurse -Force $build }
    & $cmake -S $here -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_TOOLCHAIN_FILE=$toolchain" "-DANDROID_ABI=$abi" `
        '-DANDROID_PLATFORM=android-26' '-DCMAKE_BUILD_TYPE=Release' "-DLIBARCHIVE_SOURCE=$Source"
    if ($LASTEXITCODE) { throw "cmake configure failed for $abi" }
    & $cmake --build $build --target comictv_archive
    if ($LASTEXITCODE) { throw "build failed for $abi" }
    New-Item -ItemType Directory -Force (Join-Path $app $abi) | Out-Null
    $out = Join-Path $app "$abi\libcomictv_archive.so"
    Copy-Item (Join-Path $build 'libcomictv_archive.so') $out -Force
    & $strip --strip-unneeded $out
    Write-Host "$abi -> $out ($((Get-Item $out).Length) bytes)"
}
