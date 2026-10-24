import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { strict as assert } from 'node:assert';

const source = readFileSync(new URL('../app/src/main/java/com/pelican/comictv/library/LibraryDatabase.kt', import.meta.url), 'utf8');
const migration = source.match(/db\.execSQL\("([^"]+)"\)/)?.[1];
function query(name) {
  const expression = new RegExp('@Query\\("([^"]+)"\\)\\s*suspend fun ' + name + '\\b');
  const result = source.match(expression)?.[1];
  assert.ok(result, 'Missing DAO SQL for ' + name);
  return result;
}
const db = new DatabaseSync(':memory:');
try {
  db.exec('CREATE TABLE comics (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, pageCount INTEGER NOT NULL, currentPage INTEGER NOT NULL, lastRead INTEGER NOT NULL, importedAt INTEGER NOT NULL, fit INTEGER NOT NULL, rtl INTEGER NOT NULL)');
  db.exec("INSERT INTO comics VALUES ('keep', 'Original', 20, 12, 123, 100, 2, 1)");
  assert.ok(migration);
  db.exec(migration);
  const row = () => ({ ...db.prepare("SELECT * FROM comics WHERE id = 'keep'").get() });
  const original = row();
  assert.equal(original.hiddenFromHistory, 0);
  db.prepare(query('setHistoryHidden')).run({ ':id': 'keep', ':hidden': 1 });
  assert.deepEqual(row(), { ...original, hiddenFromHistory: 1 });
  db.prepare(query('setHistoryHidden')).run({ ':id': 'keep', ':hidden': 0 });
  assert.deepEqual(row(), original);
  db.prepare(query('setHistoryHidden')).run({ ':id': 'keep', ':hidden': 1 });
  db.prepare(query('saveProgress')).run({ ':id': 'keep', ':page': 99, ':time': 456 });
  assert.deepEqual(row(), { ...original, hiddenFromHistory: 0, currentPage: 19, lastRead: 456 });
  assert.equal(db.prepare('SELECT COUNT(*) AS total FROM comics').get().total, 1);
  console.log('PASS: actual migration and DAO SQL preserve editions/progress; hiding is reversible and reading restores history.');
} finally { db.close(); }
