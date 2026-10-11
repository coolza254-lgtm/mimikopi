# Testing with the Badger C-918

The C-918's MIDI behaviour is **unverified** (key count, velocity sensitivity, USB-MIDI class
compliance, sustain pedal output). This checklist finds out. Send the results back
(Diagnostics → คัดลอก JSON covers most of it).

## You need
- A USB-C cable that carries **data** (charge-only cables will not work). If the piano has a USB-B port,
  a USB-B to USB-C data cable; if it has USB-C, a USB-C to USB-C data cable.
- ミミコピ v0.5.0 or newer.

## Steps
1. Turn the piano on. Plug the cable into the piano and the phone.
   - If the phone asks what to use USB for, choose **MIDI** (One UI may show "Transferring files" by default).
2. Open **ต่อเปียโน (MIDI)**. Under "อุปกรณ์ที่ต่ออยู่" the piano should appear within a few seconds with
   **เชื่อมต่อแล้ว**. Write down the name shown.
   - Nothing appears → try the cable the other way round / another data cable; check One UI USB mode.
     If it never appears, the piano may not be USB-MIDI class compliant (report this).
3. Play a **C major scale** (C4 to C5) slowly. The monitor should show 8 `ON`/`OFF` pairs with the right
   note names. If every note is off by 12 (e.g. C5 shown for C4) run **ตั้งอ็อกเทฟ**.
4. Run **ทดสอบแรงกด**: 5 very soft presses, then 5 very hard presses. The result says whether the piano is
   velocity-sensitive. (If it is fixed, dynamics never become a pass condition.)
5. Press the **lowest and highest keys**; note the numbers shown in the monitor (88 keys = 21..108,
   61 keys usually 36..96).
6. If you have a **sustain pedal** plugged into the piano: press and release it; the monitor should show
   `PEDAL ลง` / `PEDAL ขึ้น`. If nothing shows, the piano does not send CC64 over USB.
7. Decide whether you want the app's piano sound while playing the real piano (switch "เล่นเสียงเปียโนของแอพ…").
8. Run **วัดความหน่วง** with the setup you will practise with (e.g. piano + phone speaker; repeat with
   Bluetooth headphones if you use them). Report the ms values.
9. Play some chords and fast notes for a minute: no stuck or missing notes? Unplug the cable while holding a
   key: the sound should stop, and replugging should reconnect on its own.
10. Diagnostics → **คัดลอก JSON** and send it.
