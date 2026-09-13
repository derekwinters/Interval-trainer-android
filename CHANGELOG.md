# Changelog

## [0.1.0](https://github.com/derekwinters/Interval-trainer-android/compare/v0.0.1...v0.1.0) (2026-09-13)


### Features

* adopt ai-sdlc v0.4.22 ([#6](https://github.com/derekwinters/Interval-trainer-android/issues/6)) ([53986c1](https://github.com/derekwinters/Interval-trainer-android/commit/53986c1345bbd9358e5f23d79d065e713184f582)), closes [#2](https://github.com/derekwinters/Interval-trainer-android/issues/2)
* bootstrap the Gradle build and the Android app module ([#16](https://github.com/derekwinters/Interval-trainer-android/issues/16)) ([6449f5e](https://github.com/derekwinters/Interval-trainer-android/commit/6449f5e70a7d4f7f77ea4e4e7b34a0ece4c89e8b))
* pin the release signing certificate and gate every release APK against it ([#17](https://github.com/derekwinters/Interval-trainer-android/issues/17)) ([35e3201](https://github.com/derekwinters/Interval-trainer-android/commit/35e320181aac03131290f588e798c74cb5f03215))
* **skills:** install wayfinder and its supporting skills from mattpocock/skills ([#21](https://github.com/derekwinters/Interval-trainer-android/issues/21)) ([c5a8d2a](https://github.com/derekwinters/Interval-trainer-android/commit/c5a8d2af01c34823eb937d8d9997fea8a80e31c7)), closes [#20](https://github.com/derekwinters/Interval-trainer-android/issues/20)


### Fixes

* reset the recorded version to 0.0.1 so the first release is 0.1.0 ([#19](https://github.com/derekwinters/Interval-trainer-android/issues/19)) ([3e74226](https://github.com/derekwinters/Interval-trainer-android/commit/3e74226c4fb22175b79b4719e95dba1ae5d3e5ad)), closes [#18](https://github.com/derekwinters/Interval-trainer-android/issues/18)


### Documentation

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
* set up wayfinder on GitHub with managed labels, a tracker doc and the closing rule ([#34](https://github.com/derekwinters/Interval-trainer-android/issues/34)) ([8c4ff92](https://github.com/derekwinters/Interval-trainer-android/commit/8c4ff928b17b80f9e53d03dc0625010fc8dc7ae9)), closes [#23](https://github.com/derekwinters/Interval-trainer-android/issues/23)
* specify the timer state machine and reconcile the cue page ([#55](https://github.com/derekwinters/Interval-trainer-android/issues/55)) ([7c9d6bc](https://github.com/derekwinters/Interval-trainer-android/commit/7c9d6bc3d1f1167009c5bf6acad568789c4a6b35)), closes [#28](https://github.com/derekwinters/Interval-trainer-android/issues/28)
* **spec:** specify the interval cues ([#51](https://github.com/derekwinters/Interval-trainer-android/issues/51)) ([2a7cedf](https://github.com/derekwinters/Interval-trainer-android/commit/2a7cedfbbf5ad0291e29399e427c42a0d7c4ac16)), closes [#30](https://github.com/derekwinters/Interval-trainer-android/issues/30)
