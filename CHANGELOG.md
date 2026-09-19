# Changelog

## [0.2.1](https://github.com/derekwinters/Interval-trainer-android/compare/v0.2.0...v0.2.1) (2026-09-19)


### Fixes

* **ci:** stop release-please failing when no pending release PR exists ([c4e84cb](https://github.com/derekwinters/Interval-trainer-android/commit/c4e84cb65940855accfdbd56c15db8a8c9b1f6fb)), closes [#114](https://github.com/derekwinters/Interval-trainer-android/issues/114)

## [0.2.0](https://github.com/derekwinters/Interval-trainer-android/compare/v0.1.0...v0.2.0) (2026-09-19)


### Features

* **app:** add the first-run screen and permission-request flow ([65245e8](https://github.com/derekwinters/Interval-trainer-android/commit/65245e8494286245f015883edfc4310d892ba6e0))
* **app:** add the home screen ([a0dc7f5](https://github.com/derekwinters/Interval-trainer-android/commit/a0dc7f55bedcee946fb5ada049ec4faa3af7b4d9))
* **app:** add the preset editor screen ([c886b0f](https://github.com/derekwinters/Interval-trainer-android/commit/c886b0f24865a31541e3abd474af1011c9ca776e))
* **app:** add the running screen ([6a514da](https://github.com/derekwinters/Interval-trainer-android/commit/6a514daa3129128b9c2ea2c5324e0aad293164c4))
* **app:** add the settings screen ([fabfbaa](https://github.com/derekwinters/Interval-trainer-android/commit/fabfbaa577e10732e2a3b91d62771f2318686be7))
* **app:** add the summary screen ([d48fe76](https://github.com/derekwinters/Interval-trainer-android/commit/d48fe76418176eaf74d24e1218c4bc96af1a42f9))
* **build:** create :core module and move formatSeconds into it ([92346ac](https://github.com/derekwinters/Interval-trainer-android/commit/92346ac6f17466380a78e2b54703066025a255d7)), closes [#65](https://github.com/derekwinters/Interval-trainer-android/issues/65)
* **build:** raise minSdk from 24 to 26 ([db015da](https://github.com/derekwinters/Interval-trainer-android/commit/db015da54919492d0b3c25a3cec456f5ff64f9d5)), closes [#64](https://github.com/derekwinters/Interval-trainer-android/issues/64)
* **build:** wire up Compose, Material 3's placeholder-free shell, and Navigation in :app ([8620152](https://github.com/derekwinters/Interval-trainer-android/commit/8620152e50eb2200090f0027f6c2fb371b4e5790)), closes [#66](https://github.com/derekwinters/Interval-trainer-android/issues/66)
* **core:** add preset/interval model and the schedule-copy function ([d9e3743](https://github.com/derekwinters/Interval-trainer-android/commit/d9e37431f1f3b22dd342accc322e5ace74196c4b))
* **core:** implement cue selection ([c159219](https://github.com/derekwinters/Interval-trainer-android/commit/c159219547c5502a38411232772ae2292f6de961))
* **core:** implement the round generator ([a549ec3](https://github.com/derekwinters/Interval-trainer-android/commit/a549ec324c2aa4b028d4fb9cf4c04a178d905f17))
* **core:** implement the timer state machine ([0637a64](https://github.com/derekwinters/Interval-trainer-android/commit/0637a645505763b6758a3d386071ef754cd97ac1))
* **database:** add the :database module and presets/intervals schema ([4c11ab6](https://github.com/derekwinters/Interval-trainer-android/commit/4c11ab62510eb9600fd4c28bdd31fe6fbcf47787))
* **database:** add the schema upgrade-path contract-test harness ([b016c92](https://github.com/derekwinters/Interval-trainer-android/commit/b016c9275cd8677d1f38f79ea8d05e34ae1ea7e8))
* **database:** seed the two endurance presets on first database creation ([1f55032](https://github.com/derekwinters/Interval-trainer-android/commit/1f5503225b3c2d6b863aab29c3be8d8a334213fb))
* **designsystem:** add the closed set of three screen layouts ([e98311e](https://github.com/derekwinters/Interval-trainer-android/commit/e98311e9751ea02f2772b2cdedbadb9aeb7edf63))
* **designsystem:** add the component gallery and Robolectric semantics-tree tests ([231bb0c](https://github.com/derekwinters/Interval-trainer-android/commit/231bb0c21dac4201f287e46382038beb67102016))
* **designsystem:** add the component vocabulary from design-system.md ([e9c31e8](https://github.com/derekwinters/Interval-trainer-android/commit/e9c31e8851aef9ee55901f5d31d81881e3d46efd))
* **designsystem:** stand up the :designsystem module and the token layer ([54ee3e8](https://github.com/derekwinters/Interval-trainer-android/commit/54ee3e8dc7c6d8fbe5d4ad7f8b40638894188e8e)), closes [#68](https://github.com/derekwinters/Interval-trainer-android/issues/68)
* **service:** add the foreground service and its notification ([ce6a8f7](https://github.com/derekwinters/Interval-trainer-android/commit/ce6a8f75c358a6909245627ea670592352f8a29d))

## [0.1.0](https://github.com/derekwinters/Interval-trainer-android/compare/v0.0.1...v0.1.0) (2026-09-15)


### Features

* adopt ai-sdlc v0.4.22 ([#6](https://github.com/derekwinters/Interval-trainer-android/issues/6)) ([53986c1](https://github.com/derekwinters/Interval-trainer-android/commit/53986c1345bbd9358e5f23d79d065e713184f582)), closes [#2](https://github.com/derekwinters/Interval-trainer-android/issues/2)
* bootstrap the Gradle build and the Android app module ([#16](https://github.com/derekwinters/Interval-trainer-android/issues/16)) ([6449f5e](https://github.com/derekwinters/Interval-trainer-android/commit/6449f5e70a7d4f7f77ea4e4e7b34a0ece4c89e8b))
* **build:** build a release candidate on release-please's pull request ([a746ddc](https://github.com/derekwinters/Interval-trainer-android/commit/a746ddc7b6d86b01b8b82e3174f1ab5b4b8b5240)), closes [#13](https://github.com/derekwinters/Interval-trainer-android/issues/13)
* **build:** build the release APK in the release-please run and attach it ([81c0f43](https://github.com/derekwinters/Interval-trainer-android/commit/81c0f43bdbafb618bc9ba0392ce31d5144799a4b))
* **build:** bump VERSION_CODE on every release pull request ([4aa9319](https://github.com/derekwinters/Interval-trainer-android/commit/4aa931935a6c9189794f51a5b6da872d1233a41c))
* **build:** version the app from gradle.properties via release-please ([e367178](https://github.com/derekwinters/Interval-trainer-android/commit/e367178d791d74bde366e11693771355fc1a6648))
* pin the release signing certificate and gate every release APK against it ([#17](https://github.com/derekwinters/Interval-trainer-android/issues/17)) ([35e3201](https://github.com/derekwinters/Interval-trainer-android/commit/35e320181aac03131290f588e798c74cb5f03215))
* **skills:** install wayfinder and its supporting skills from mattpocock/skills ([#21](https://github.com/derekwinters/Interval-trainer-android/issues/21)) ([c5a8d2a](https://github.com/derekwinters/Interval-trainer-android/commit/c5a8d2af01c34823eb937d8d9997fea8a80e31c7)), closes [#20](https://github.com/derekwinters/Interval-trainer-android/issues/20)


### Fixes

* drop the removed tools package from the SDK setup step ([#60](https://github.com/derekwinters/Interval-trainer-android/issues/60)) ([c26d0a0](https://github.com/derekwinters/Interval-trainer-android/commit/c26d0a08201b577db1d27ec176d861b0d9118cf2)), closes [#59](https://github.com/derekwinters/Interval-trainer-android/issues/59)
* reset the recorded version to 0.0.1 so the first release is 0.1.0 ([#19](https://github.com/derekwinters/Interval-trainer-android/issues/19)) ([3e74226](https://github.com/derekwinters/Interval-trainer-android/commit/3e74226c4fb22175b79b4719e95dba1ae5d3e5ad)), closes [#18](https://github.com/derekwinters/Interval-trainer-android/issues/18)


### Documentation

* add the home screen and preset editor prototypes ([#58](https://github.com/derekwinters/Interval-trainer-android/issues/58)) ([6a376f3](https://github.com/derekwinters/Interval-trainer-android/commit/6a376f3b7945d3bedfa433c9eebdf3d65b6f3e5f)), closes [#29](https://github.com/derekwinters/Interval-trainer-android/issues/29)
* add the project glossary and record the Compose and core-shell decisions ([#46](https://github.com/derekwinters/Interval-trainer-android/issues/46)) ([802329d](https://github.com/derekwinters/Interval-trainer-android/commit/802329d6c9990b26ac356280a1b7b1b5f363f064)), closes [#24](https://github.com/derekwinters/Interval-trainer-android/issues/24)
* add the running-screen prototype (ring timer + schedule rail) ([#57](https://github.com/derekwinters/Interval-trainer-android/issues/57)) ([1f00046](https://github.com/derekwinters/Interval-trainer-android/commit/1f00046f6129a7051afe8b9b16e406f7d59054a1))
* **adr:** record Room from v1 with the schema treated as an API ([#45](https://github.com/derekwinters/Interval-trainer-android/issues/45)) ([6b97b50](https://github.com/derekwinters/Interval-trainer-android/commit/6b97b500210b9e94625776e19c834b8c2780313f)), closes [#32](https://github.com/derekwinters/Interval-trainer-android/issues/32)
* **adr:** record that specifications stay as docs/spec pages for v1 ([#50](https://github.com/derekwinters/Interval-trainer-android/issues/50)) ([b63d804](https://github.com/derekwinters/Interval-trainer-android/commit/b63d804bbe336b42f70e5c259c4fda3b3477aff8)), closes [#41](https://github.com/derekwinters/Interval-trainer-android/issues/41)
* **adr:** record that the foreground service owns the running workout ([#44](https://github.com/derekwinters/Interval-trainer-android/issues/44)) ([1845d2a](https://github.com/derekwinters/Interval-trainer-android/commit/1845d2a804d634b9f708845369b58d37da94dc5a)), closes [#31](https://github.com/derekwinters/Interval-trainer-android/issues/31)
* amend ADR 0003 and the glossary for editable interval lists ([#53](https://github.com/derekwinters/Interval-trainer-android/issues/53)) ([f363b4a](https://github.com/derekwinters/Interval-trainer-android/commit/f363b4a5d06888a5ef21463ea77853362c1c93a0)), closes [#52](https://github.com/derekwinters/Interval-trainer-android/issues/52)
* rename the rest interval kind to recovery in the glossary ([#47](https://github.com/derekwinters/Interval-trainer-android/issues/47)) ([a37c83c](https://github.com/derekwinters/Interval-trainer-android/commit/a37c83c86f91d7aa9b775fb7e4da285b7c297744))
* **research:** land the cue audio and vibration research note ([#36](https://github.com/derekwinters/Interval-trainer-android/issues/36)) ([9480e28](https://github.com/derekwinters/Interval-trainer-android/commit/9480e284df5c084a90ecfd0f3578cd40d6921490)), closes [#26](https://github.com/derekwinters/Interval-trainer-android/issues/26)
* **research:** land the foreground service research note ([#37](https://github.com/derekwinters/Interval-trainer-android/issues/37)) ([28ed3b8](https://github.com/derekwinters/Interval-trainer-android/commit/28ed3b8224448f214cf30f4c5e81bbeb1918fc21)), closes [#25](https://github.com/derekwinters/Interval-trainer-android/issues/25)
* **research:** land the OpenSpec and OKF research note ([#42](https://github.com/derekwinters/Interval-trainer-android/issues/42)) ([c6a2fd4](https://github.com/derekwinters/Interval-trainer-android/commit/c6a2fd4e5bbbea3f758b5c87ba69bfbc5f611589)), closes [#39](https://github.com/derekwinters/Interval-trainer-android/issues/39)
* **research:** land the Room migration testing research note ([#35](https://github.com/derekwinters/Interval-trainer-android/issues/35)) ([9c9bd79](https://github.com/derekwinters/Interval-trainer-android/commit/9c9bd7972c51684a4ea776f57b1e50531667bc69)), closes [#27](https://github.com/derekwinters/Interval-trainer-android/issues/27)
* **research:** land the UI consistency research note ([#43](https://github.com/derekwinters/Interval-trainer-android/issues/43)) ([6134972](https://github.com/derekwinters/Interval-trainer-android/commit/61349728d997d0c5ffc5b696d916734b4b2e95fe)), closes [#38](https://github.com/derekwinters/Interval-trainer-android/issues/38)
* resolve the design system as ADR 0007 and a design-system spec ([e872531](https://github.com/derekwinters/Interval-trainer-android/commit/e87253135609d0e7f2336b18bd871a9267242bb5)), closes [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40)
* set up wayfinder on GitHub with managed labels, a tracker doc and the closing rule ([#34](https://github.com/derekwinters/Interval-trainer-android/issues/34)) ([8c4ff92](https://github.com/derekwinters/Interval-trainer-android/commit/8c4ff928b17b80f9e53d03dc0625010fc8dc7ae9)), closes [#23](https://github.com/derekwinters/Interval-trainer-android/issues/23)
* specify the timer state machine and reconcile the cue page ([#55](https://github.com/derekwinters/Interval-trainer-android/issues/55)) ([7c9d6bc](https://github.com/derekwinters/Interval-trainer-android/commit/7c9d6bc3d1f1167009c5bf6acad568789c4a6b35)), closes [#28](https://github.com/derekwinters/Interval-trainer-android/issues/28)
* **spec:** specify the interval cues ([#51](https://github.com/derekwinters/Interval-trainer-android/issues/51)) ([2a7cedf](https://github.com/derekwinters/Interval-trainer-android/commit/2a7cedfbbf5ad0291e29399e427c42a0d7c4ac16)), closes [#30](https://github.com/derekwinters/Interval-trainer-android/issues/30)
* **spec:** write the v1 specification (schema, service, screens) ([2541878](https://github.com/derekwinters/Interval-trainer-android/commit/25418787d10450d3d57446812ff0795849edff38))
* stop claiming the spec-to-test traceability gate is enforced ([adead09](https://github.com/derekwinters/Interval-trainer-android/commit/adead09cd67c5b65c40d9cfe8dce180f79b74ae3)), closes [#49](https://github.com/derekwinters/Interval-trainer-android/issues/49)
* **timer:** decide the two skip behaviours left unspecified ([c25e753](https://github.com/derekwinters/Interval-trainer-android/commit/c25e7534c07d389bb02d166f9028b3342f4006ce)), closes [#56](https://github.com/derekwinters/Interval-trainer-android/issues/56)
