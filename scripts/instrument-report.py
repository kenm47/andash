#!/usr/bin/env python3
"""Summarise `adb shell am instrument -r` output: pass/fail per test, with stack heads for failures."""
import re, sys
s = open(sys.argv[1]).read()
ok = fail = 0
for b in s.split("INSTRUMENTATION_STATUS: class=")[1:]:
    t = re.search(r"test=(\w+)", b).group(1)
    code = re.search(r"STATUS_CODE: (-?\d)", b).group(1)
    if code == "0":
        ok += 1
        print("  ok  ", t)
    elif code in ("-1", "-2"):
        fail += 1
        st = re.search(r"stack=(.*?)INSTRUMENTATION_STATUS: stream", b, re.S)
        print("  FAIL", t, "\n      ", (st.group(1)[:600] if st else b[:400]).strip().replace("\n", "\n       "))
print(f"{ok} passed, {fail} failed")
sys.exit(1 if fail else 0)
