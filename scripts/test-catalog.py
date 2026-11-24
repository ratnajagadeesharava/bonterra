"""Builds docs/TEST-CASES.md from the @DisplayName of every test, with case counts from surefire reports."""
import collections
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

src_root = pathlib.Path(sys.argv[1])
reports = pathlib.Path(sys.argv[2])

# surefire testcase: classname = class display name, name = method name (+ invocation suffix)
counts = collections.Counter()
failed = collections.Counter()
for f in reports.glob("TEST-*.xml"):
    for tc in ET.parse(f).getroot().iter("testcase"):
        method = re.split(r"[(\[]", tc.get("name") or "")[0].strip()
        counts[(tc.get("classname"), method)] += 1
        if tc.find("failure") is not None or tc.find("error") is not None:
            failed[(tc.get("classname"), method)] += 1


def order(p):
    return "F09a" if p.name == "MoneyTest.java" else p.name


files = sorted(src_root.rglob("*Test.java"), key=order)
DISPLAY = re.compile(r'@DisplayName\("((?:[^"\\]|\\.)*)"\)')
ANNOTATION = r'@\w+(?:\((?:"(?:[^"\\]|\\.)*"|\{[^}]*\}|[^()"{}])*\))?'
TEST = re.compile(r"((?:" + ANNOTATION + r"\s*)+)\s*void (\w+)\(")

out, total = [], 0
for p in files:
    src = p.read_text(encoding="utf-8")
    cls = re.search(r'@DisplayName\("([^"]+)"\)\s*class', src).group(1)
    doc = re.search(r"/\*\*(.*?)\*/\s*@DisplayName\(\"[^\"]+\"\)\s*class", src, re.S)
    text = ""
    if doc:
        lines = [l.strip(" *") for l in doc.group(1).splitlines()]
        text = " ".join(l for l in lines if l)
        text = re.sub(r"\{@link #?([^}]+)\}", r"`\1`", text).replace("<p>", "")
    rel = p.as_posix()[p.as_posix().index("src/"):]
    out.append(f"\n## {cls}\n\n[{p.name}]({'../' + rel})\n\n{text}\n\n| # | Test case | Cases | Result |\n|---|---|---|---|")
    n = 0
    for m in TEST.finditer(src):
        ann = m.group(1)
        d = DISPLAY.search(ann)
        if not d or not re.search(r"@(Test|ParameterizedTest|RepeatedTest)\b", ann):
            continue
        n += 1
        c = counts.get((cls, m.group(2)), 0)
        bad = failed.get((cls, m.group(2)), 0)
        result = "not run" if c == 0 else ("pass" if bad == 0 else f"{bad} of {c} failed")
        total += c or 1
        out.append(f"| {n} | {d.group(1).replace('|', '/')} | {c or 1} | {result} |")

ran = sum(counts.values())
failures = sum(failed.values())
status = (f"**Last run:** {ran} cases ran, {ran - failures} passed, {failures} failed."
          if ran else "**Last run:** not run.")

header = (
    "# Business test cases\n\n"
    f"Generated from the `@DisplayName` of every test, in flow order: **{total} test cases** in total, "
    "counting each parameterized row and each repetition.\n\n"
    "Each case runs against the service layer on a fresh SQLite file reset to the seed in "
    "[Fixtures.java](../src/test/java/org/communitybridge/service/Fixtures.java), with the clock fixed at "
    "2026-11-24T09:30:00Z. A failure case also checks that every row of every table is identical before and after.\n\n"
    + status + " Regenerate with `.\\mvnw.cmd test` then "
    "`python scripts/test-catalog.py src/test/java target/surefire-reports > docs/TEST-CASES.md`.\n"
)
sys.stdout.reconfigure(encoding="utf-8")
print(header + "\n".join(out))
