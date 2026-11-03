/*
 * Comic TV: RAR reading through libarchive (BSD-2-Clause), for com.pelican.comictv.archive.LibArchiveRar.
 * Only the RAR and RAR5 readers are compiled in. Names cross JNI as raw bytes and are decoded in Kotlin,
 * because JNI strings use modified UTF-8. Every decoded byte goes to the Kotlin sink, which enforces the
 * size limits and cancellation by throwing; a pending Java exception stops the native loop at once.
 */
#include <jni.h>
#include <string.h>
#include <archive.h>
#include <archive_entry.h>

#define FLAG_DIRECTORY 1
#define FLAG_LINK 2
#define FLAG_ENCRYPTED 4
#define FLAG_SIZE_UNKNOWN 8
#define FLAG_SPECIAL 16
#define CHUNK 65536

static void throw_io(JNIEnv *env, const char *message) {
    if ((*env)->ExceptionCheck(env)) return;
    jclass io = (*env)->FindClass(env, "java/io/IOException");
    if (io != NULL) (*env)->ThrowNew(env, io, message != NULL ? message : "libarchive error");
}

static struct archive *open_rar(JNIEnv *env, jstring path) {
    const char *file = (*env)->GetStringUTFChars(env, path, NULL);
    if (file == NULL) return NULL;
    struct archive *a = archive_read_new();
    if (a == NULL) { (*env)->ReleaseStringUTFChars(env, path, file); throw_io(env, "out of memory"); return NULL; }
    archive_read_support_format_rar(a);
    archive_read_support_format_rar5(a);
    int r = archive_read_open_filename(a, file, CHUNK);
    (*env)->ReleaseStringUTFChars(env, path, file);
    if (r != ARCHIVE_OK) {
        throw_io(env, archive_error_string(a));
        archive_read_free(a);
        return NULL;
    }
    return a;
}

static int entry_flags(struct archive_entry *e) {
    int flags = 0;
    mode_t type = archive_entry_filetype(e);
    if (type == AE_IFDIR) flags |= FLAG_DIRECTORY;
    else if (type != AE_IFREG) flags |= FLAG_SPECIAL;
    if (archive_entry_symlink(e) != NULL || archive_entry_hardlink(e) != NULL || type == AE_IFLNK) flags |= FLAG_LINK;
    if (archive_entry_is_encrypted(e)) flags |= FLAG_ENCRYPTED;
    if (!archive_entry_size_is_set(e)) flags |= FLAG_SIZE_UNKNOWN;
    return flags;
}

static jbyteArray name_bytes(JNIEnv *env, struct archive_entry *e) {
    const char *name = archive_entry_pathname_utf8(e);
    if (name == NULL) name = archive_entry_pathname(e);
    if (name == NULL) name = "";
    jsize length = (jsize) strnlen(name, 4096);
    jbyteArray bytes = (*env)->NewByteArray(env, length);
    if (bytes != NULL) (*env)->SetByteArrayRegion(env, bytes, 0, length, (const jbyte *) name);
    return bytes;
}

/* sink.entry(byte[] name, long size, int flags) for each header, in archive order; returns the count read. */
JNIEXPORT jint JNICALL
Java_com_pelican_comictv_archive_LibArchiveRar_nativeEntries(JNIEnv *env, jclass cls, jstring path, jint max, jobject sink) {
    (void) cls;
    jclass sinkClass = (*env)->GetObjectClass(env, sink);
    jmethodID entry = (*env)->GetMethodID(env, sinkClass, "entry", "([BJI)V");
    if (entry == NULL) return -1;
    struct archive *a = open_rar(env, path);
    if (a == NULL) return -1;
    struct archive_entry *e;
    jint count = 0;
    int r;
    while ((r = archive_read_next_header(a, &e)) == ARCHIVE_OK || r == ARCHIVE_WARN) {
        if (++count > max) { throw_io(env, "too many entries"); break; }
        jbyteArray name = name_bytes(env, e);
        if (name == NULL) break;
        (*env)->CallVoidMethod(env, sink, entry, name, (jlong) archive_entry_size(e), (jint) entry_flags(e));
        (*env)->DeleteLocalRef(env, name);
        if ((*env)->ExceptionCheck(env)) break;
        /* Headers only: skipping does not decode non-solid data. */
        if (archive_read_data_skip(a) != ARCHIVE_OK) { throw_io(env, archive_error_string(a)); break; }
    }
    if (r == ARCHIVE_FATAL || r == ARCHIVE_FAILED) throw_io(env, archive_error_string(a));
    archive_read_free(a);
    return count;
}

/*
 * Decodes the entries up to [index] and streams the bytes to sink.chunk(boolean target, byte[] buffer, int length).
 * Earlier entries are decoded too (target=false), since a solid RAR needs them; their bytes are discarded by the sink.
 * Returns the number of bytes of the target entry.
 */
JNIEXPORT jlong JNICALL
Java_com_pelican_comictv_archive_LibArchiveRar_nativeExtract(JNIEnv *env, jclass cls, jstring path, jint index, jboolean solidReplay, jobject sink) {
    (void) cls;
    jclass sinkClass = (*env)->GetObjectClass(env, sink);
    jmethodID chunk = (*env)->GetMethodID(env, sinkClass, "chunk", "(Z[BI)V");
    if (chunk == NULL) return -1;
    jbyteArray buffer = (*env)->NewByteArray(env, CHUNK);
    if (buffer == NULL) return -1;
    struct archive *a = open_rar(env, path);
    if (a == NULL) return -1;
    char data[CHUNK];
    struct archive_entry *e;
    jint current = 0;
    jlong total = -1;
    int r;
    while ((r = archive_read_next_header(a, &e)) == ARCHIVE_OK || r == ARCHIVE_WARN) {
        jboolean target = current == index;
        if (target || (solidReplay && archive_entry_filetype(e) == AE_IFREG)) {
            jlong written = 0;
            for (;;) {
                la_ssize_t n = archive_read_data(a, data, CHUNK);
                if (n == 0) break;
                if (n < 0) { throw_io(env, archive_error_string(a)); goto done; }
                (*env)->SetByteArrayRegion(env, buffer, 0, (jsize) n, (const jbyte *) data);
                (*env)->CallVoidMethod(env, sink, chunk, target, buffer, (jint) n);
                if ((*env)->ExceptionCheck(env)) goto done;
                written += n;
            }
            if (target) { total = written; goto done; }
        } else if (archive_read_data_skip(a) != ARCHIVE_OK) {
            throw_io(env, archive_error_string(a));
            goto done;
        }
        current++;
    }
    if (r == ARCHIVE_FATAL || r == ARCHIVE_FAILED) throw_io(env, archive_error_string(a));
    else throw_io(env, "entry not found");
done:
    archive_read_free(a);
    (*env)->DeleteLocalRef(env, buffer);
    return total;
}

JNIEXPORT jstring JNICALL
Java_com_pelican_comictv_archive_LibArchiveRar_nativeVersion(JNIEnv *env, jclass cls) {
    (void) cls;
    return (*env)->NewStringUTF(env, archive_version_details());
}
