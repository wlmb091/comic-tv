plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.pelican.comictv"
    compileSdk = 36
    // JVM OSGi metadata from network modules is not used by Android.
    packaging.resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    // Post-quantum parameter tables (Picnic LowMC, SIKE) that BouncyCastle ships for PDFBox: PDF decryption never reads them.
    packaging.resources.excludes += "org/bouncycastle/pqc/crypto/**"
    // Compressed native libraries keep the APK small: versions before 1.4.31 accept updates up to 64 MiB, later ones 150 MiB.
    packaging.jniLibs.useLegacyPackaging = true
    // libmpv, libtorrent4j and libass each bring libc++_shared.so; the copy in src/main/jniLibs is libmpv's, the newest NDK of the three.
    packaging.jniLibs.pickFirsts += "lib/*/libc++_shared.so"
    defaultConfig {
        applicationId = "com.pelican.comictv"
        minSdk = 26
        targetSdk = 36
        versionCode = 105
        versionName = "1.4.31"
        // TVs and phones are ARM; libtorrent4j is only shipped for ARM, and x86 only adds PC/emulator size.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        getByName("debug") {
            providers.gradleProperty("comicTv.debugKeystore").orNull?.let { storeFile = file(it) }
        }
    }
    // The debug build installs next to the signed release, so test builds never replace the installed app or its data.
    buildTypes { release { isMinifyEnabled = false }; debug { applicationIdSuffix = ".dev" } }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":core"))
    implementation(project(":mihon"))
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    // libass for ASS/SSA with original styles and fonts embedded in MKV files (MIT/ISC).
    implementation("io.github.peerless2012:ass-media:0.5.1")
    // Official Media3 FFmpeg (LGPL audio decoders: DTS, TrueHD, AC-3, E-AC-3, Opus, FLAC...) and AV1 modules,
    // built by .github/workflows/media3-decoders.yml. Used only when the device decoder cannot play the track.
    implementation(files("libs/media3-decoder-ffmpeg-1.8.0.aar", "libs/media3-decoder-av1-1.8.0.aar"))
    implementation("androidx.media3:media3-decoder:1.8.0")
    // mpv 0.41 with FFmpeg built with --enable-gpl (GPLv3): the default engine of the Comic TV player.
    implementation("dev.jdtech.mpv:libmpv:1.0.0")
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-38")
    runtimeOnly("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-38")
    runtimeOnly("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-38")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    // Already packaged transitively by Coil; declare for direct artwork metadata validation.
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
