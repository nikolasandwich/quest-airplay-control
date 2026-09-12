# Overlay control lock 0.2.22

A 48dp lock button at the right center toggles top status and bottom controls. It remains available to restore controls; hidden in settings and PiP. This is a UI visibility lock, not a mouse control lock. Same SurfaceView retained; reset ray accumulation on toggle. Generic pointer events over lock bypass global HID handling. Visibility survives activity recreation but starts unlocked on fresh launch. All 76 tests, APK build, lint and 158-key localization check passed. Not installed; no physical visual or pointer interaction verification yet.
