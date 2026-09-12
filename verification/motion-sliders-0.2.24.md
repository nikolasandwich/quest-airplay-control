# Motion sliders 0.2.24

Settings adds drag distance and scroll speed sliders, 25–200 percent in 5 percent increments, default 100. Preferences persist and update service immediately while settings disarms input. PointerAction snapshots drag gain per action, keeps ten bounded moves then release. Scroll button actions snapshot wheel amount across six reports. Existing event-based vertical scroll uses gain and amplitude; no timer-driven output added. Horizontal input remains one drag per push. Quest supplies mapped scroll deltas, not direct physical pressure/raw stick axes; no claim of raw pressure support. Ray movement sensitivity unaffected.

79 tests passed including drag distances in both directions, release completion and scroll gain/no residual output. APK, lint, 162-key four-language parity and generated fallback checks passed. Not installed; physical slider interaction not yet verified. User confirmed previous BLE reconnect fix works.
Installed successfully by explicit user request on 2026-09-12 18:58:05; verified code 31, launched PID 15510. APK SHA256 edfcd062d9a3e7947797da3665b98e097a42e3d6b9f425735df1d6d84e62a14a. No automatic input or slider changes.
