# ชุดคลาสสิกไล่ขั้น — v0.26.8

ลำดับแนะนำสำหรับ **ฉบับเรียบเรียงในแอป** ไม่ใช่เกรด ABRSM หรือการรับรองว่าเล่นเพลงต้นฉบับเต็มได้
เพลงเปิดทุกขั้นได้ทันที ฝึกด้วยคีย์บนมือถือหรือ MIDI เลือกแยกมือ รอให้กดถูก ตามจังหวะ และวนช่วงได้เหมือนเพลงเดิม
ระดับ L เป็นค่าประเมินของแอป การเล่นเพลงไม่เปลี่ยนระดับทักษะที่ได้จากแบบทดสอบ

| ขั้น | เพลง | สิ่งที่เพิ่ม |
|---|---|---|
| 1 | Ode to Joy — เริ่มห้าโน้ต | 8 ห้อง จังหวะย่อ ไม่มีเขบ็ต มือซ้ายเป็นเบสค้าง/พัก |
| 2 | Ode to Joy — ฉบับเดิม | 8 ห้อง มีจังหวะประจุดและเขบ็ต |
| 3 | Brahms’ Lullaby Op.49 No.4 | ท่อนร้องแรก ย้ายเป็น C มีโน้ตรับและจังหวะ 3/4 |
| 4 | Chopin Prelude in E minor Op.28 No.4 — เบสง่าย | 8 ห้องแรกพร้อมห้องรับ ย่อมือซ้ายเหลือเสียงต่ำสุด |
| 5 | Chopin Heroic Polonaise Op.53 — ฉบับง่าย | ห้อง 17–24 ย้ายเป็น C ย่อทำนอง/จังหวะและเบสค้าง ยังมี B♭ |
| 6 | Pachelbel Canon in D | ไวโอลินแนวแรก 8 ห้อง พร้อมเบสที่แอปเพิ่ม |
| 7 | Mozart Eine kleine Nachtmusik | ฉบับเดิม ท่อนเปิด 4 ห้อง ตัวหยุดและเขบ็ต |
| 8 | Petzold Minuet in G | ฉบับเดิม ท่อนแรก 16 ห้อง มือซ้ายย่อ |
| 9 | Beethoven Für Elise | ฉบับเดิม ท่อนเปิด ประสานสองมือและเครื่องหมายแปลงเสียง |
| 10 | Chopin Prelude in E minor — คอร์ดเคลื่อน | ทำนองตรงกับขั้น 4 ทุกโน้ต เปลี่ยนมือซ้ายเป็นคอร์ดเขบ็ต |
| 11 | Bach Prelude in C BWV846 | ฉบับเดิม 8 ห้อง มือซ้ายย่อ ขวามืออาร์เปจจิโอ |
| 12 | Chopin Heroic Polonaise Op.53 — ฉบับฝึกใน A♭ | ท่อนเดียวกับขั้น 5 เพิ่มคอร์ด/งานสองมือ ย่อโน้ตประดับและทริเปิล |
| 13 | Mozart Sonata K.545 | 8 ห้องแรก คงโน้ตหลัก ตัด trill/ไดนามิก/สเลอร์/เลขนิ้ว |

ทั้งชุดเป็นท่อนย่อสำหรับฝึก ไม่ใช่เพลงเต็ม รายชื่อขั้นแยกจากระดับ L และไม่ล็อกเพลง
ฉบับ Chopin Prelude in E minor เขียน 4/4 แทน 2/2 โดยคงความยาวโน้ตเดิม ใช้ bpm แบบโน้ตดำ เติมพักสามจังหวะก่อนโน้ตรับ
และหยุดหลังห้องที่ 8 ของต้นฉบับ ไม่ใส่เพดัล rubato หรือไดนามิก จึงไม่ได้ประเมินการตีความเพลงของ Chopin

## แหล่งโน้ตและสิทธิ์

บทประพันธ์ต้นฉบับของหกไฟล์ที่เพิ่มในชุดแรกเป็นสาธารณสมบัติ ฉบับโน้ตสมัยใหม่อาจมีเงื่อนไขต่างกัน จึงตรวจข้อความสิทธิ์ในแต่ละไฟล์แหล่งอ้างอิง และระบุเครดิต การดัดแปลง และลิงก์ไว้ใน `source` ของเพลง
ในแอปเปิดอ่านได้ที่ เพลง → ที่มาและการเรียบเรียง; ข้อความเลือกคัดลอกได้

ใช้ MutopiaProject ที่ commit `2144afd6f52d56c5b6995b8b589ef1268b3139f0` ตรวจเมื่อ 21 ตุลาคม 2026

- [ode](https://github.com/MutopiaProject/MutopiaProject/blob/2144afd6f52d56c5b6995b8b589ef1268b3139f0/ftp/BeethovenLv/ode/ode.ly): Peter Chubb; Mutopia-2009/08/05-528; Public Domain. ย้ายทำนองเป็น C ย่อจังหวะและเพิ่มเบสเอง การเรียบเรียงใหม่ CC0 1.0
  - SHA-256 ของไฟล์โน้ตอ้างอิงที่อ่าน: `a0c865aa9f71221605362622d281405071e0734540e86ce131131757cc803a71`
- [lullaby](https://github.com/MutopiaProject/MutopiaProject/blob/2144afd6f52d56c5b6995b8b589ef1268b3139f0/ftp/BrahmsJ/O49/Wiegenlied/Wiegenlied-lys/Wiegenlied.ly): 森 章吾 (Shogo Mori); Mutopia-2007/11/04-1037; Public Domain. ใช้ท่อนร้องแรก ย้าย E♭ เป็น C เติมพักและเพิ่มเบสเอง ไม่ใส่เนื้อร้อง การเรียบเรียงใหม่ CC0 1.0
  - SHA-256 ของไฟล์โน้ตอ้างอิงที่อ่าน: `f5b2ee47a7b0be8810f3f992243c42593ff97819ee204852c697249f6ae9668c`
- [canon](https://github.com/MutopiaProject/MutopiaProject/blob/2144afd6f52d56c5b6995b8b589ef1268b3139f0/ftp/PachelbelJ/CanonInD/CanonInD.ly): Jim Paterson (www.mfiles.co.uk), Anonymous typesetter; © 2009; Mutopia-2009/09/07-1700. CC BY 3.0. ใช้ทำนอง 8 ห้องแรกและเพิ่มเบสเอง ไฟล์ดัดแปลง canon-in-d-study.json ใช้ CC BY 3.0 เช่นกัน
  - SHA-256 ของไฟล์โน้ตอ้างอิงที่อ่าน: `d0909b505b57f0ddcc21f867346b91ca527bd30521414d997bd516548fefcb2b`
- [chopin](https://github.com/MutopiaProject/MutopiaProject/blob/2144afd6f52d56c5b6995b8b589ef1268b3139f0/ftp/ChopinFF/O28/Chop-28-4/Chop-28-4.ly): Magnus Lewis-Smith, revised Javier Ruiz-Alma; Peters 1879; Mutopia-2016/10/28-468; Public Domain. สองฉบับ: เบสย่อ / คอร์ดท่อนเปิด ไม่ใส่ไดนามิก เพดัล สเลอร์ การเรียบเรียงใหม่ทั้งสองไฟล์ CC0 1.0
  - SHA-256 ของไฟล์โน้ตอ้างอิงที่อ่าน: `d4fca574bc3c3ad810ee643d42b7299e1c97b3b8b3fa379c3d7f77edb02f6192`
- [mozart](https://github.com/MutopiaProject/MutopiaProject/blob/2144afd6f52d56c5b6995b8b589ef1268b3139f0/ftp/MozartWA/KV545/K545-1/K545-1.ly): Alejandro Sierra; © 2013; Mutopia-2013/09/01-998. CC BY-SA 3.0. ใช้ 8 ห้องแรก ตัดเครื่องประดับ การแสดงอารมณ์ และเลขนิ้ว ไฟล์ดัดแปลง mozart-k545-opening.json เผยแพร่ภายใต้ CC BY-SA 3.0
  - SHA-256 ของไฟล์โน้ตอ้างอิงที่อ่าน: `84d0e951e6d8807d3286f31ed7878a35dfddb9850bbae9156be0f5ac0a3e2e10`

ใบอนุญาต:
- CC0 1.0: https://creativecommons.org/publicdomain/zero/1.0/
- CC BY 3.0: https://creativecommons.org/licenses/by/3.0/
- CC BY-SA 3.0: https://creativecommons.org/licenses/by-sa/3.0/

เงื่อนไขข้างต้นใช้กับไฟล์โน้ตที่ระบุ ไม่ได้เปลี่ยนใบอนุญาตของซอฟต์แวร์หรือไฟล์อื่นในแอป ไม่มีการใช้เสียงบันทึกเชิงพาณิชย์ ภาพสแกนฉบับขาย หรือเนื้อร้อง

เพลงเดิมในขั้น 2, 7–9, 11 ไม่ได้เปลี่ยนโน้ตจาก v0.26.5; ส่วนที่เดิมระบุว่าเขียนจากความจำและยังไม่เทียบฉบับพิมพ์ยังคงระบุข้อจำกัดนั้นในแหล่งข้อมูล ไม่ถือว่าผ่านการตรวจเทียบเพิ่มในรอบนี้

## Heroic Polonaise Op.53

ใช้ข้อมูลโน้ตฉบับพิมพ์ครั้งแรกของ Breitkopf & Härtel ห้อง 17–24 ไม่ใช่บทนำห้อง 1–16

```text
First Editions of Fryderyk Chopin's Music
Copyright 2017-2021 The Fryderyk Chopin Institute (https://nifc.pl)
Website: https://chopinscores.org
Digital scores: https://github.com/pl-wnifc/humdrum-chopin-first-editions
License: https://creativecommons.org/licenses/by/4.0/
```

ผู้บันทึกข้อมูล Wojciech Jakubiak-Danek (2019-08-15), บรรณาธิการดิจิทัล Mateusz Mrugas (2020-04-04)

- [ข้อมูลโน้ตที่ใช้](https://github.com/pl-wnifc/humdrum-chopin-first-editions/blob/95dfb105c1669c72d10b04088566154f12d3dc1c/kern/053-1-BH.krn)
- [ใบอนุญาตและข้อความเครดิตที่ต้นทางกำหนด](https://github.com/pl-wnifc/humdrum-chopin-first-editions/blob/95dfb105c1669c72d10b04088566154f12d3dc1c/LICENSE.txt)
- SHA-256 ของไฟล์โน้ตอ้างอิง: `c09d247ce3ec9732a2ae916b57823a327fb36b329e502dea74b11e5898535e4b`

ไฟล์ `heroic-polonaise-easy.json` และ `heroic-polonaise-theme.json` เผยแพร่ภายใต้ CC BY 4.0 พร้อมเครดิตใน `source`:
- ฉบับง่าย: ย้ายจาก A♭ เป็น C, เลือกทำนองแนวเดียว, ย่อจังหวะและโน้ตผ่านบางตัว, ย้ายอ็อกเทฟบางช่วงให้จำกัดช่วง C4–E5, เพิ่มเบสค้างเอง ยังคงมี B♭
- ฉบับฝึกใน A♭: คงคีย์และรูปจังหวะหลัก, ยุบเสียงซ้อนบางจุด, ย่อทริเปิลและกลุ่มเร็วที่มีโน้ต 32 เป็นเขบ็ตหนึ่งชั้น, ลดอ็อกเทฟ/การกระโดดมือซ้าย
- ทั้งสองฉบับตัดเลขนิ้ว สเลอร์ เครื่องประดับ ไดนามิก เพดัล ไม่ใช่ต้นฉบับเต็ม จบท่อนก่อนจบวลีใหญ่ และไม่ประเมินการตีความหรือพลังเสียงของต้นฉบับ

แหล่ง IMSLP ระบุบทประพันธ์และฉบับเก่าเป็น Public Domain; ไม่ได้ใช้ไฟล์สแกน IMSLP ในการถอดโน้ตครั้งนี้ เนื่องจากหน้าดาวน์โหลดแสดง bot check ใช้ข้อมูลดิจิทัลที่สถาบันเผยแพร่โดยตรงแทน
