# Mihon runtime

The Android `mihon` library hosts the extension API from OtakuWorld
`nightly_2026_10_16` (Apache-2.0), derived from Mihon commit
`446f6df82c390ab760802c2fce3b677bce3f31c1`. See `mihon/VENDORED.md` and its licenses.
It supports the 1.2-1.6 API range, including the 1.6 suspend `getMangaUpdate`
bridge while retaining legacy Rx methods. Compatibility depends on each APK's
additional libraries and required capabilities.

`MihonEngine(context)` lists installed extensions and loads a package only after
`load(packageName, expectedFingerprint)` verifies its current SHA-256 certificate.
The caller supplies the explicit trusted repository/user pin. Unsigned packages,
mismatches, unsupported API versions and malformed metadata fail before executing
source constructors. Current package version and signers are checked again before
operations. Package failures are thrown to the caller for an isolated UI error;
one package never implicitly loads another. Android extensions run within the
host process and have the host's permissions; certificate trust is essential.

Android23-27 use legacy signature APIs; Android28+ use current APK signers.
Android27+ use DelegateLastClassLoader; 23-26 use DexClassLoader. Installed extension
visibility requires QUERY_ALL_PACKAGES, declared in the library manifest because
extensions advertise a feature, not necessarily an activity/intent.

Catalog, details, chapters and pages retain extension ordering. Downloads call the
HTTP source's own `getImageUrl`/`getImage`, preserving source clients, cookies,
headers and per-page overrides. Original Page objects are retained in a bounded
5000-entry LRU (the maximum accepted chapter). Downloads enforce 32MiB (or a smaller caller limit), use sibling
`.part` files, fsync and atomically rename after success, and delete partial files
on failure/cancellation. The app owns image dimension validation, cache eviction,
reader progress and UI. The network helper has a separate 5MiB HTTP cache.

QuickJS is explicitly unsupported and raises an explanatory exception when used.
HQNow's currently inspected implementation does not require JavaScript. Challenge
handling uses the vendored WebView interceptor and depends on a working Android
System WebView; this is not a guarantee every site's anti-bot challenge succeeds.

Tests: ExtensionPolicyTest covers class expansion, fingerprint normalization,
certificate mismatch/empty/additional signer rejection and API range. The Android
smoke test checks runtime initialization and API bridge presence. A real APK
class-loading test needs an Android device, an installed trusted fixture and its
known fingerprint. No APK is installed or executed on the development PC.

Android23 WebView compatibility uses the upstream challenge-status bridge fallback
rather than the newer isolated-world APIs (WebKit1.17 requires Android24).
Rx coroutine cancellation hooks are registered before subscription starts, enabling
legacy synchronous HTTP calls to cancel without waiting for network timeouts.
