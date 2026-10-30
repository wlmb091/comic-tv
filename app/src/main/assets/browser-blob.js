/* Comic TV: retain an already-created, same-page Blob while the user confirms saving it. */
(function () {
    'use strict';
    if (window.__comicTvBlobV1) return;
    const entries = new Map();
    const release = key => {
        const entry = entries.get(key);
        if (entry) clearTimeout(entry.timer);
        entries.delete(key);
    };
    window.__comicTvBlobV1 = {
        prepare: function (url, key, maximum) {
            try {
                if (entries.size >= 4) throw new Error('limit');
                if (new URL(url).origin !== location.origin) throw new Error('origin');
                let name = '';
                const links = document.querySelectorAll('a[download]');
                for (let i = 0; i < Math.min(links.length, 1024); i++) {
                    if (links[i].href === url) { name = String(links[i].download || '').slice(0, 240); break; }
                }
                const entry = { url: url, name: name, active: false };
                // Start now: providers can revoke the original URL before the dialog is answered.
                entry.promise = fetch(url).then(response => response.blob()).then(blob => {
                    if (blob.size > maximum) throw new Error('size');
                    return blob;
                }).catch(() => null);
                entry.timer = setTimeout(() => { if (!entry.active) release(key); }, 300000);
                entries.set(key, entry);
                return JSON.stringify({ ready: true, name: name });
            } catch (_) { return JSON.stringify({ ready: false }); }
        },
        take: async function (key, url) {
            const entry = entries.get(key);
            if (!entry || entry.url !== url) throw new Error('expired');
            entry.active = true;
            const blob = await entry.promise;
            if (!blob) throw new Error('expired');
            return { blob: blob, name: entry.name };
        },
        park: function (key) {
            const entry = entries.get(key);
            if (entry) {
                entry.active = false;
                clearTimeout(entry.timer);
                entry.timer = setTimeout(() => { if (!entry.active) release(key); }, 300000);
            }
        },
        release: release
    };
})();
