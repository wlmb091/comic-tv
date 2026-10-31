# Companion gestures and keyboard Implementation Plan

> Execute inline task by task; focused regressions and independent review before publication.

**Goal:** PDF3pages +video13.47s and chat requests: reader controls perreference, contextual mouse/pinch, independent sensitivities, retractabletabs, keyboardstable, Menu→Home.
**Architecture:** existing HTML/CSS/PointerEvents and tokenLAN commands, nativeReader/WebView owncontext. No thirdpartycode, no Safari toolbar tricks. Persistent gestureprefs onTV (temporaryport/origin changes make browserstorage unreliable). Nochangephysicalremote.
**Authorization:** User explicitly requested ALL chat changes and authorized generating/publishing after complete30/10/2026. Prepare1.4.7/code81.

- [x] RED gesture/keyboard/layout regressions, inspectvideo/PDFallpages. Rootcause: bodyheight resized but notanchored to visualViewport.offsetTop; keyboard autofocus pans document whileflexminimum and form overflow interact. Existing Menu sendsKEYCODE_MENU withoutHomecallback.
- [x] PDFreaderlayout: next/righttop, previous/rightbottom, pagecountercenter; zoomhorizontal and contextualsensitivitybelow. Preservepage slider/reset ascompactcontrols. Keyboardfixedshell visualviewportheight+offset; input16px/preventScroll, shrinkpad whilekeyboardopen; close/restorenormal. Errorsconnection separate.
- [x] Pointerstate machine: onefingerpan/cursor, readerpinchabsolutezoom, browser2fingersscroll/pinchrelativezoom, tapclick/longcontext/tapdrag; abort/captureloss/background reset, bound30cmd/s. Client sensitivities reader/pointer/zoom/scroll .25–3, nativepersist andvalidate. Stalecontext failsclosed; noOSremote.
- [x] BottomtabsretractableonlyReader/Control, explicitsmallbutton notgestureconflict; shareSafariHomeScreeninstructions, temporarytoken/IPport stilllimitsreopening.
- [x] Nativehomecallback/browseroperations incltimeoutdragrelease, serverbounds+tests. FocusedJVM/Node/Androidcompile/lint and Edgefixtures withkeyboardviewportoffset. Independentreview. PhysicalSafari/Boxpending.
- [x] Release1.4.7 singleCI/sign/verifyassetmanifesthash thenpublishrecord.

Ruling: incluirjanelaModalprópria porqueo novo toqueLongo abririaummenuincontrolável. RegistroCompositionLocalexplícitosemreflexão,externalwindowcontinuaexcluída;revisãoindependenteconferida.
