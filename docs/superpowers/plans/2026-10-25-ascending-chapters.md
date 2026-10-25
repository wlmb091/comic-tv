# Ascending chapter order and paused narration plan

Fully specified user correction: source descending order places chapter01last; numericascending must be default andmanualorder offered. Additional user request pauses read-aloud/translation untilfuture; slowpageonlyonline. Do not promise speedup fromdisabling alreadylazyfeatures.

- [x] ReproduceactualJVMfailure source[10,2,1],current1,nextmust2: ascending118-red8tests1failure.
- [x] Addenumorderedsequence overload reusingexisting numericparser/metadata/dedup; keeprawsourceorderinternally.
- [x] VM/UI/prefs useAsc/Desc/Source; defaultAsc inreaderanddetails; newper-titlekeyv2 avoidsambiguouslegacyBooleaninversion withoutdeletingdata. Persistnewchoice; failurekeepsoldchoice.
- [x] Disable narration/translationentrypoints viafeatureconstant; retainimplementation/preferences/dependencyforfuture. No TTS/client/PDFtextextractor initializedfromdisabledentrypoints; EPUBtext/PDFimage ordinaryreading preserved.
- [x] Fulltests/builds/lint/nativecompile, freshreview, signing1.1.8/code47 anddelivery. Readeronlinewaiting notprovenimproved; userreportslocalsfast, firstpage pathawaitsnetworkandoptionalcovergeneration; noblindcachechanges.
