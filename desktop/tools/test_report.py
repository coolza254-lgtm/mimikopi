"""Prints failed tests (with their messages) and test counts from the Gradle XML reports. CI only reads logs."""
import glob
import sys
import xml.etree.ElementTree as ET

groups = [
    ("core", "core/build/test-results/test/*.xml"),
    ("platform", "desktop/platform/build/test-results/test/*.xml"),
    ("app", "desktop/app/build/test-results/test/*.xml"),
]
for name, pattern in groups:
    total = failed = skipped = 0
    for path in glob.glob(pattern):
        root = ET.parse(path).getroot()
        total += int(root.get("tests"))
        failed += int(root.get("failures")) + int(root.get("errors"))
        skipped += int(root.get("skipped"))
        for case in root.iter("testcase"):
            for bad in list(case.findall("failure")) + list(case.findall("error")):
                print("FAILED", case.get("classname"), case.get("name"))
                print((bad.text or "")[:4000])
    print(f"{name}: {total} tests, {failed} failed, {skipped} skipped")
sys.exit(0)
