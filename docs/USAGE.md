# Usage examples

These are usage walkthroughs for the 0.2.29 preview, not screenshots or claims
that every sender app has been tested. The receiver runs on Quest 3; your
iPhone/iPad continues to run the original apps.

## Example 1: Browse an iPad page

1. Open the Quest receiver and connect both devices to the same Wi-Fi network.
2. On iPad, choose **Control Center → Screen Mirroring** and select the receiver
   name displayed in Quest. Wait for the mirrored picture.
3. Select **Connect mouse** in Quest, then pair/reconnect the Quest mouse in
   the iPad's Bluetooth settings. Basic iPad mouse input normally does not
   require AssistiveTouch.
4. Return to the mirrored view and select **Enable control**. Open a page on
   iPad, move its pointer with the Quest ray, and use the stick or scroll
   buttons to scroll. A click acts at the iPad pointer's current position.
5. Select **Pause control** when you want to use the tablet directly.

Screen mirroring and mouse pairing are separate connections. Seeing a picture
does not by itself mean that mouse control is connected or enabled.

## Example 2: Use an iPhone

Follow the same mirroring steps. Before pairing the mouse, enable
**Settings → Accessibility → Touch → AssistiveTouch** on iPhone. Then pair the
Quest mouse in Bluetooth settings and select **Enable control** in Quest.

The round iPhone pointer is supported by experimental shape-and-motion alignment.
The AssistiveTouch floating menu can interfere with recognition. If tracking feels
unstable, move the menu out of the working area or compare with alignment assist
disabled in Quest Settings. Tracking is not guaranteed to stay pixel-perfect.

## Example 3: Adjust controls and simplify the view

| Goal | Action |
|---|---|
| Change app language | Open **Settings → Language**, select a language and apply. System-following mode falls back to English when no translation matches. |
| Adjust horizontal gestures | In **Settings**, change **Horizontal drag distance** (25–200%). |
| Adjust vertical scrolling | In **Settings**, change **Scroll speed** (25–200%). This does not change pointer movement speed. |
| Hide the top and bottom bars | Select the lock on the right. Mouse control can continue while the bars are hidden. |
| Show the bars again | Point at the lock's position on the right to reveal it, then select it. |
| Review the basics | Reopen the beginner guide or button descriptions from **Settings**. |

After leaving Settings, enable control again. **Restore control** disables
alignment assist and returns to relative control; it does not center the pointer.

## Expected limits

- Left/right actions are mouse drags; up/down actions are scrolling. They are not
  arbitrary iOS system touch gestures, and the sender app determines the result.
- Losing the usable mirrored view, foreground or screen-on context disarms input.
  Bluetooth can stay paired; returning does not automatically arm control.
- Pause before removing the headset. Immediate physical removal detection has
  not been established.
- This is an unofficial preview, not an Apple or Meta product or endorsement.

See [device validation](../verification/quest-0.2.29-device-validation.md) for
what was actually checked. Real screenshots will be added when a usable capture
is available; no mockup is presented here as a device screenshot.
