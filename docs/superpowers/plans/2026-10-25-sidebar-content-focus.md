# Stable sidebar/content focus plan

Two explicit user bugs, demonstrated in supplied videos: returning Right from lower drawer row scrolls a top page; loading Catalog/Recent expands drawer without cursor entry. Keep page offset on handoff and drawer collapsed until actually focused.

- [x] Inspect both videos via contact sheets, trace group/spatial focus and transient child replacement.
- [x] Native regressions written before productcode: lowerdrawerRight preservesoffset untilDown, latecontentreplacement retainscontentfocus, updatedrouteconfirmationfocus.
- [x] StablefocusableentryoutsideLazy containers, explicitRightfromdrawer; Down/OK enterschildgroup, Leftreturnsselectedsidebar. Consumeactivationrelease; no visual layout change or forced scrollToTop. Keepstartup/back-specificrestore unchanged.
- [x] FullJVM/builds/lint/nativecompile, onefreshreview andsign1.1.9/code48. No ADBdevice means no executed UI RED/performanceclaim.

The neutral entry is deliberately a separate focus target with content semantics/accessibility action. It keeps focus across loading, and entering actual item navigation is explicit rather than inheriting the sidebar row's Y coordinate.
