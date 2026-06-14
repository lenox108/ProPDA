# Performance Checklist

## Startup
- Cold start measured.
- Warm start measured.
- App startup after process death checked.

## Topic screen
- Topic open time measured.
- Large topic scroll smoothness checked.
- WebView reload time checked.
- Back navigation latency checked.
- Toolbar/header does not jump during loading.

## Memory
- Memory after opening 5 large topics checked.
- Memory after image-heavy topic checked.
- Memory after rotation/reload checked.
- largeHeap requirement documented or removed with evidence.

## Media/WebView
- Image-heavy topic checked.
- Avatar intercept time and encoded response size checked.
- Avatar/image intercept path checked.
- No repeated full DOM rebuilds during scroll.
- No obvious GC spikes while scrolling.
- `largeHeap=false` smoke test checked on large topics before removing `android:largeHeap`.

## Regression rule
Before and after performance-sensitive patches, record:
- device/emulator
- build variant
- topic used for test
- measured result
- notes

## largeHeap Removal Blocker

`android:largeHeap="true"` remains in `AndroidManifest.xml` until memory is
measured after opening several large and image-heavy topics. Do not remove it
without confirming topic open, reload, image viewer, and scrolling behavior with
`largeHeap=false`.
