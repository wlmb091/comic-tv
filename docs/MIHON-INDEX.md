# Mihon repository indexes

`MihonRepoClient` is a blocking JVM reader. Invoke it from an IO dispatcher. Its optional `(String) -> ByteArray` transport supports deterministic tests; production uses HTTPS-only `HttpURLConnection` with bounded redirects and timeouts.

Accepted inputs: HTTPS descriptor, repository directory, direct JSON/protobuf index, and GitHub repository URLs with optional explicit `tree`, `blob`, or `raw` branch. A bare GitHub repository uses the conventional `repo` branch. Supply an explicit branch when a repository uses another branch.

Descriptors preserve `meta.signingKeyFingerprint`. V2 supports protobuf and proto3 JSON, inline extension list field 101, external list URL field 102, and gzip. Legacy `index.min.json` is supported, but obsolete Outdated App migration stubs fail with an actionable error. Direct legacy indexes have no signing metadata; use a descriptor for a trusted installation flow. Existing certificate pins must be checked by the caller before replacing persisted repository metadata.

Bounds: 8 MiB network response, 16 MiB decompressed response, 10,000 extensions, 1,000 sources per extension, 64 JSON nesting levels, one external-list hop. All descriptor, list, APK and icon URLs require HTTPS. Reading an index never fetches or executes an APK.

Field mapping reference (Apache-2.0 OtakuWorld, tag `nightly_2026_10_16`):
https://github.com/jakepurple13/OtakuWorld/blob/nightly_2026_10_16/mihonsupport/src/commonMain/kotlin/com/programmersbox/mihon/repo/MihonIndexV2.kt

Current Keiyoushi descriptor uses `index_v2` pointing to `index.pb`:
https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json

Synthetic JUnit tests cover JSON, gzip/protobuf, legacy entries, URL normalization, malformed/oversized responses, recursive external lists and excessive extension counts. Real repository reads require network validation separately; no APK execution is needed.

Repository input URLs reject query strings and fragments. Resource URLs can retain HTTPS query strings. The second real repository validated with the JVM client is https://raw.githubusercontent.com/Suwayomi/tachiyomi-extension/repo/repo.json: one Suwayomi extension, version 1.4.29. Yuzono manga-repo is retired: its legacy index contains only migration stubs, and is correctly rejected.
