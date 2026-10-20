# Stamina HUD

On the supported 720 × 1280 City and World views, the can and current stamina
value share the HUD region `(8,101)` to `(105,126)`. Tapping that region opens
the existing Obtain more dialog; stamina is read from its fraction region, not
from the profile screen.

After five rejected OCR attempts, Frostguard captures the current dialog frame
as `logs/snapshot/stamina/<utc>-ocr-failed.png` before one Android Back returns
to City or World. The global diagnostic-snapshot setting still controls this:
when it is off, the warning records `snapshot=disabled` and no frame is kept.
The snapshot store validates PNG conversion, retains the newest 20 stamina
captures, and captures the desktop only when its separate global setting is on.
