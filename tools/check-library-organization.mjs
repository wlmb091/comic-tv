import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { strict as assert } from 'node:assert';
const source = readFileSync(new URL('../app/src/main/java/com/pelican/comictv/library/LibraryDatabase.kt', import.meta.url), 'utf8');
const marker = source.indexOf('val MIGRATION_2_3');
assert.ok(marker >= 0, 'Missing additive migration 2→3 for favorites and collections');
const sql = [...source.slice(marker).matchAll(/db\.execSQL\("([^"]+)"\)/g)].map(x => x[1]);
assert.ok(sql.length >= 5, 'Missing collection schema/indexes');
for (const version of [1, 2]) {
  const db = new DatabaseSync(':memory:');
  try {
    db.exec('PRAGMA foreign_keys = ON');
    db.exec('CREATE TABLE comics (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, pageCount INTEGER NOT NULL, currentPage INTEGER NOT NULL, lastRead INTEGER NOT NULL, importedAt INTEGER NOT NULL, fit INTEGER NOT NULL, rtl INTEGER NOT NULL' + (version === 2 ? ', hiddenFromHistory INTEGER NOT NULL DEFAULT 0' : '') + ')');
    db.exec("INSERT INTO comics (id,title,pageCount,currentPage,lastRead,importedAt,fit,rtl) VALUES ('keep','Original',25,12,123,100,2,1)");
    if (version === 1) db.exec('ALTER TABLE comics ADD COLUMN hiddenFromHistory INTEGER NOT NULL DEFAULT 0');
    db.exec('UPDATE comics SET hiddenFromHistory=1');
    sql.forEach(s => db.exec(s));
    const row = db.prepare("SELECT * FROM comics WHERE id='keep'").get();
    assert.equal(row.favorite, 0); assert.equal(row.currentPage, 12); assert.equal(row.hiddenFromHistory, 1);
    db.exec("UPDATE comics SET favorite=1 WHERE id='keep'");
    db.exec("INSERT INTO collections VALUES ('c1','Ação','acao'), ('c2','Outras','outras')");
    assert.throws(() => db.exec("INSERT INTO collections VALUES ('c3','ACAO','acao')"));
    db.exec("INSERT INTO comic_collections VALUES ('keep','c1'),('keep','c2')");
    assert.throws(() => db.exec("INSERT INTO comic_collections VALUES ('missing','c1')"));
    db.exec('BEGIN'); db.exec("DELETE FROM comic_collections WHERE comicId='keep'"); db.exec('ROLLBACK');
    assert.equal(db.prepare('SELECT count(*) n FROM comic_collections').get().n, 2);
    db.exec("DELETE FROM collections WHERE id='c1'");
    assert.equal(db.prepare('SELECT count(*) n FROM comic_collections').get().n, 1);
    const kept = db.prepare("SELECT * FROM comics WHERE id='keep'").get();
    assert.equal(kept.favorite, 1); assert.equal(kept.currentPage, 12); assert.equal(kept.hiddenFromHistory, 1);
    console.log(`PASS: ${version}→3 preserves editions/progress, collection constraints, rollback and cascade.`);
  } finally { db.close(); }
}
