# Extension queue — approved flow

User approved: click starts/enqueues download, another click queued/downloading cancels; several selected at once, progress visible; ready card offers Instalar. Android installer confirmation remains. No extra download confirmation, no automatic silent installation.

One APK downloading at a time, up to12 queued/ready packages and256MiB APK cache (64MiB per APK). Cancellation joins previous writer before next; completed packages retained until installer returns, no deleting another APK offered to Android. Validate repository pin/package/version/compatibility as before; revalidate prepared APK/pin before install. No APK auto-execution.

Queue owned by shared SourcesViewModel, pauses/cancels active transfer when screen/app leaves foreground, pending items preserved for return; no background service. Ready files cached, volatile queue not part of backup; process termination may leave bounded cache. Installed/cancelled installer return releases only selected cached APK. Permission denial/launch error retains ready item for retry. Multiple repo cards use same package-deduplicated queue.

Tests with fakes: ordered sequential work, toggle cancel and late completion ignored, pause/resume, dedupe/limit, failures allow next, installer protects ready file; native controls compile. Full build/lint/ABI/signature/SQL and independent review. Release1.1.3/code42; physical Android11 installer/D-pad pending.
