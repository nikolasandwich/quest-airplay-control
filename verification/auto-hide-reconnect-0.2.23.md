# Auto-hide lock and early BLE reconnection 0.2.23

Lock fades to alpha zero after three seconds when chrome is hidden. Hover reveals and holds it visible; exit schedules hiding. Invisible touch first reveals without toggling. Descriptions updated in four languages. Activity destruction removes timer. Keyboard focus reveals but does not prevent timeout (focus moved onto the only visible control otherwise prevents hiding).

Device log showed early GATT connection before final service registration, then forced cancellation with no disconnect callback. Repeated Connect mouse only logged Already started. Later physical Bluetooth cycling produced disconnect, fresh connect, subscription and successful reports. Fix retains the early host after service registration; input still requires bond, current-schema subscription, service readiness and explicit arming. Connect mouse restarts discovery when ready with no host and reports gate state otherwise. This removes the observed stuck needsReconnect gate; full iPad pointer outcome still needs user confirmation. No automatic pointer input or Bluetooth toggle.

77 tests passed, including early host retention with unarmed input, and auto-hide/hover visibility. APK build, lint and 159-key resources passed. Installed on 2026-09-12 18:15:34, code 30, PID 13513. Runtime confirms early bonded host and cached subscription retained after services ready; HTTP 200, new crash buffer empty.

API reference: https://developer.android.com/reference/android/bluetooth/BluetoothGattServer

