# Live search addendum · approved by user during keyboard correction

Local source/extension filters already update on each edit. Keep that behavior. Show matching library titles/count inside search dialog before confirmation, respecting existing history/collection filters. Remote catalog searches wait400ms after typing, cancel superseded work immediately, and publish only latest results. Clearing restores the blank-query catalog. Buscar agora and IME Search bypass delay. Cancel pending search on navigation/source change/details/manager/Back or leaving catalog. Preserve textfocus/keyboard activation correction; do not implement pending cover-performance proposal.

Tests: rapid edits issue one request, explicit immediate search, clearing query, ignored stale uncooperative response, cancellation/retry, and native librarypreviewwithoutconfirmation. Compile native tests only if no device. Update release1.1.6 notes and validation around finalcombinedscope, not just keyboard.
