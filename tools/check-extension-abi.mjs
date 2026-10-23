// Verify the actual packaged DEX, since JVM tests cannot detect D8 default-method rewriting.
// Usage: node tools/check-extension-abi.mjs <directory containing extracted classes*.dex>
import fs from 'node:fs';
import path from 'node:path';
const directory = process.argv[2];
if (!directory) throw new Error('Provide the extracted APK DEX directory.');
const target = 'Lkotlinx/serialization/internal/GeneratedSerializer;';
let found = false;
for (const name of fs.readdirSync(directory).filter(n => /^classes\d*\.dex$/.test(n))) {
  const b = fs.readFileSync(path.join(directory, name));
  if (b.subarray(0, 4).toString() !== 'dex\n') throw new Error(`Invalid DEX: ${name}`);
  const u32 = o => b.readUInt32LE(o);
  function leb(cursor) {
    let value = 0;
    for (let shift = 0; shift < 35; shift += 7) {
      const byte = b[cursor.pos++];
      if (byte === undefined) throw new Error('Truncated DEX');
      value |= (byte & 127) << shift;
      if (!(byte & 128)) return value >>> 0;
    }
    throw new Error('Invalid ULEB128');
  }
  const strings = Array.from({ length: u32(56) }, (_, i) => {
    const cursor = { pos: u32(u32(60) + i * 4) };
    leb(cursor);
    const end = b.indexOf(0, cursor.pos);
    if (end < 0) throw new Error('Unterminated string');
    return b.subarray(cursor.pos, end).toString();
  });
  const types = Array.from({ length: u32(64) }, (_, i) => strings[u32(u32(68) + i * 4)]);
  for (let i = 0; i < u32(96); i++) {
    const offset = u32(100) + i * 32;
    if (types[u32(offset)] !== target) continue;
    if (found) throw new Error('Duplicate GeneratedSerializer definition');
    found = true;
    if (!(u32(offset + 4) & 0x200)) throw new Error("GeneratedSerializer is not an interface");
    const cursor = { pos: u32(offset + 24) };
    if (!cursor.pos) throw new Error('Missing class data');
    const staticFields = leb(cursor), instanceFields = leb(cursor);
    const directMethods = leb(cursor), virtualMethods = leb(cursor);
    for (let f = 0; f < staticFields + instanceFields; f++) { leb(cursor); leb(cursor); }
    let implementation = false;
    for (const count of [directMethods, virtualMethods]) {
      let methodIndex = 0;
      for (let m = 0; m < count; m++) {
        methodIndex += leb(cursor);
        const flags = leb(cursor), code = leb(cursor);
        const methodOffset = u32(92) + methodIndex * 8;
        const methodName = strings[u32(methodOffset + 4)];
        if (methodName === 'typeParametersSerializers') {
          const proto = u32(76) + b.readUInt16LE(methodOffset + 2) * 12;
          if (types[u32(proto + 4)] !== '[Lkotlinx/serialization/KSerializer;' || u32(proto + 8) !== 0) throw new Error('Unexpected method prototype');
          if (!(flags & 1) || (flags & 8)) throw new Error('Default method must be public and non-static');
          if ((flags & 0x400) || code === 0) {
            throw new Error('Extension ABI broken: GeneratedSerializer.typeParametersSerializers is abstract in packaged DEX. Preserve interface defaults (minSdk >= 26).');
          }
          implementation = true;
        }
      }
    }
    if (!implementation) throw new Error('Missing typeParametersSerializers implementation');
  }
}
if (!found) throw new Error('GeneratedSerializer missing from APK DEX');
console.log('PASS: GeneratedSerializer.typeParametersSerializers has an executable default implementation in APK DEX.');
