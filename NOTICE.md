# Comic TV · licença e componentes

Comic TV é distribuído sob a **GNU General Public License versão 3** ([LICENSE](LICENSE)), desde a versão 1.4.31. O APK inclui bibliotecas GPL (mpv e FFmpeg compilados com `--enable-gpl`), então o aplicativo inteiro segue a GPL. O código-fonte correspondente completo é este repositório.

Os textos das licenças vão dentro do APK, em `assets/licenses/`, e também em [docs/licenses](docs/licenses).

## Bibliotecas nativas

| Componente | Licença | Origem |
|---|---|---|
| mpv 0.41.0, FFmpeg 8.1 (GPL), libass, FreeType, HarfBuzz, FriBidi, fontconfig, libunibreak, libxml2, mbedTLS, libplacebo, dav1d, Lua | GPL 2+ no conjunto; cada um com a sua licença | `dev.jdtech.mpv:libmpv:1.0.0`, [libmpv-android v1.0.0](https://github.com/jarnedemeulemeester/libmpv-android/tree/v1.0.0). Detalhes em `mpv-GPL-NOTICE.txt` |
| libarchive 3.8.11 (leitura de RAR) | BSD 2-Clause | [libarchive](https://github.com/libarchive/libarchive/releases/tag/v3.8.11), compilado por [native/archive/build.ps1](native/archive/build.ps1) |
| FFmpeg de áudio do Media3 (libffmpegJNI) | LGPL 2.1+ | `.github/workflows/media3-decoders.yml`, `FFmpeg-LGPL-NOTICE.txt` |
| libgav1 (AV1 do ExoPlayer) | Apache 2.0 | mesmo workflow, `libgav1-NOTICE.txt` |
| libass do ExoPlayer (`io.github.peerless2012:ass-media`) | ISC / MIT | `libass-NOTICE.txt` |
| libtorrent4j | MIT | `libtorrent4j-LICENSE.md` |

## Bibliotecas Java e Kotlin

AndroidX, Media3, Jetpack Compose, Coil, OkHttp e kotlinx (Apache 2.0); PDFBox Android e BouncyCastle; Apache Commons Compress, IO, Lang e Codec; Zip4j; XZ; Jsoup; NanoHTTPD; ZXing; Gson; SLF4J. As licenças de cada uma estão em `docs/licenses`.

## Fontes tipográficas

Inter, Montserrat, Bebas Neue, Barlow Condensed e Cinzel, sob a SIL Open Font License 1.1 (`docs/licenses/*-OFL.txt`).

## Só nos testes

O Junrar (licença UnRAR, incompatível com a GPL) é usado apenas pelos testes JVM do módulo `core` e não faz parte do APK.
