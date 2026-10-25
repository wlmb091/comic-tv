# Synthetic archive fixtures

These archives contain only two copies of an original 1×1 PNG used for tests, not user comics.

`collection-solid-rar5.rar` wraps the existing two synthetic page fixtures as `2.cbr` and `10.cbr`, physically stored in reverse order using RAR 7.01 CLI `a -ma5 -m1 -s -ep`; tests import both editions in natural order without user content.
RAR4 fixtures use stored entries and headers generated locally with CRC32. `traversal.rar` and `duplicate.rar` deliberately contain invalid names. RAR5 is a solid archive generated with RAR CLI (`-ma5 -s -md1m -ep`). Password fixture uses the public test password `fixture`. Do not extract malicious-name fixtures to the filesystem.
