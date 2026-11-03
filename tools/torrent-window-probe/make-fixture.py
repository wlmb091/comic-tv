"""Build a deterministic sparse-fixture torrent, and package the actual compiled app torrent classes."""
import hashlib
import struct
import sys
import zipfile
from pathlib import Path

out, repo, native_jar = map(Path, sys.argv[1:4])
size = 1_516_383_963
piece = 524_288
hashes = []
for index in range((size + piece - 1) // piece):
    data = struct.pack(">I", index) + bytes(min(piece, size - index * piece) - 4)
    hashes.append(hashlib.sha1(data).digest())

def encode(value):
    if isinstance(value, int):
        return b"i" + str(value).encode() + b"e"
    if isinstance(value, bytes):
        return str(len(value)).encode() + b":" + value
    return b"d" + b"".join(encode(k) + encode(v) for k, v in sorted(value.items())) + b"e"

info = {b"name": b"synthetic.mp4", b"length": size, b"piece length": piece, b"pieces": b"".join(hashes)}
(out / "synthetic.torrent").write_bytes(encode({b"info": info}))
with zipfile.ZipFile(out / "probe-input.jar", "w") as archive:
    for path in out.glob("TorrentProbe*.class"):
        archive.write(path, path.name)
    classes = repo / "app/build/tmp/kotlin-classes/debug"
    for path in (classes / "com/pelican/comictv/tv").glob("*.class"):
        if path.name.startswith(("TvTorrentSession", "TvTorrentEngine", "TorrentStats")):
            archive.write(path, path.relative_to(classes).as_posix())
# Extended paths are needed for the existing long Windows toolchain directory.
jar_path = str(native_jar.resolve())
if sys.platform == "win32":
    jar_path = '\\\\?\\' + jar_path
with zipfile.ZipFile(jar_path) as archive:
    (out / "libtorrent4j.so").write_bytes(archive.read("lib/armeabi-v7a/libtorrent4j.so"))
