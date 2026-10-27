#!/usr/bin/env bash
# walkthrough.sh — drive the Splitwise Offline app in an emulator/phone via adb.
#
# Creates a "WeekendTrip" group, adds 3 members (Carol, Bob, Alice), and two
# expenses (Dinner 120 paid by Carol, Gas 45 paid by Bob). Captures a screenshot
# after each step into ./screenshots_out/.
#
# Prerequisites: a booted emulator or USB-connected phone with the app installed.
#   adb install -r app/build/outputs/apk/debug/app-debug.apk
#
# Usage:   ./docs/walkthrough.sh [output_dir]
set -euo pipefail

OUT="${1:-screenshots_out}"
mkdir -p "$OUT"

ADB="${ADB:-adb}"
tap()  { $ADB shell input tap "$1" "$2"; }
text() { $ADB shell input keyboard text "$1"; }
kbd_back() { $ADB shell input keyevent KEYCODE_BACK; }
shot() { $ADB exec-out screencap -p > "$OUT/$1"; }
dump() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB pull /sdcard/ui.xml /tmp/ui_dump.xml >/dev/null 2>&1
}

# Centre of a [x1,y1][x2,y2] bounds string.
center() { python3 -c "
import re,sys
m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]','$1')
x1,y1,x2,y2=map(int,m.groups())
print(f'{(x1+x2)//2} {(y1+y2)//2}')
"; }

find_el() {  # find_el <text-or-desc>  → prints bounds
  python3 -c "
import xml.etree.ElementTree as ET,sys
q=sys.argv[1]
t=ET.parse('/tmp/ui_dump.xml')
for e in t.iter():
    txt=e.attrib.get('text','') or ''
    desc=e.attrib.get('content-desc','') or ''
    if q==txt or q==desc:
        print(e.attrib.get('bounds','')); break
" "$1"
}

tap_el() {  # tap_el <text-or-desc>
  dump
  local b; b=$(find_el "$1")
  [ -z "$b" ] && { echo "element not found: $1" >&2; return 1; }
  local c; c=$(center "$b")
  echo "  tap '$1' @ $c"
  tap $c
}

echo "==> Launching app"
$ADB shell am force-stop dev.splitwise.offline
$ADB shell monkey -p dev.splitwise.offline -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 5
shot 01_groups_list_empty.png

echo "==> Create group 'WeekendTrip'"
tap_el "New group"; sleep 2
# Group name field is the first EditText
dump
b=$(python3 -c "
import xml.etree.ElementTree as ET
t=ET.parse('/tmp/ui_dump.xml')
for e in t.iter():
    if e.attrib.get('class','')=='android.widget.EditText':
        print(e.attrib.get('bounds',''));break
")
c=$(center "$b"); tap $c; sleep 1
text "WeekendTrip"; sleep 1
kbd_back; sleep 1
tap_el "Create"; sleep 3
shot 02_group_detail_empty.png

echo "==> Add members: Carol, Bob, Alice"
for name in Carol Bob Alice; do
  tap_el "Add member"; sleep 2
  dump
  b=$(python3 -c "
import xml.etree.ElementTree as ET
t=ET.parse('/tmp/ui_dump.xml')
for e in t.iter():
    if e.attrib.get('class','')=='android.widget.EditText':
        print(e.attrib.get('bounds',''));break
")
  c=$(center "$b"); tap $c; sleep 1
  text "$name"; sleep 1
  kbd_back; sleep 1
  tap_el "Add"; sleep 2
done
shot 03_group_detail_members.png

echo "==> Add expense: Dinner 120, paid by Carol, split among all"
tap_el "Add expense"; sleep 3
dump
# Description = first EditText, Amount = second EditText
b=$(python3 -c "
import xml.etree.ElementTree as ET
t=ET.parse('/tmp/ui_dump.xml')
es=[e for e in t.iter() if e.attrib.get('class','')=='android.widget.EditText']
print(es[0].attrib.get('bounds',''))
")
c=$(center "$b"); tap $c; sleep 1; text "Dinner"; sleep 1; kbd_back; sleep 1
b=$(python3 -c "
import xml.etree.ElementTree as ET
t=ET.parse('/tmp/ui_dump.xml')
es=[e for e in t.iter() if e.attrib.get('class','')=='android.widget.EditText']
print(es[1].attrib.get('bounds',''))
")
c=$(center "$b"); tap $c; sleep 1; text "120"; sleep 1; kbd_back; sleep 1
tap_el "Save expense"; sleep 3
shot 04_group_detail_one_expense.png

echo "==> Add expense: Gas 45, paid by Bob, split among all"
tap_el "Add expense"; sleep 3
dump
b=$(python3 -c "
import xml.etree.ElementTree as ET
t=ET.parse('/tmp/ui_dump.xml')
es=[e for e in t.iter() if e.attrib.get('class','')=='android.widget.EditText']
print(es[0].attrib.get('bounds',''))
")
c=$(center "$b"); tap $c; sleep 1; text "Gas"; sleep 1; kbd_back; sleep 1
b=$(python3 -c "
import xml.etree.ElementTree as ET
t=ET.parse('/tmp/ui_dump.xml')
es=[e for e in t.iter() if e.attrib.get('class','')=='android.widget.EditText']
print(es[1].attrib.get('bounds',''))
")
c=$(center "$b"); tap $c; sleep 1; text "45"; sleep 1; kbd_back; sleep 1
# select Bob as payer (2nd "Paid by" row — tap the checkbox next to "Bob")
dump
b=$(python3 -c "
import xml.etree.ElementTree as ET
t=ET.parse('/tmp/ui_dump.xml')
# find the CheckBox whose following sibling text is 'Bob' in the 'Paid by' section
elems=list(t.iter())
for i,e in enumerate(elems):
    if e.attrib.get('class','')=='android.widget.CheckBox':
        # look ahead for a TextView with text 'Bob'
        for e2 in elems[i:i+4]:
            if (e2.attrib.get('text','') or '')=='Bob':
                print(e.attrib.get('bounds',''));break
        else: continue
        break
")
c=$(center "$b"); tap $c; sleep 1
tap_el "Save expense"; sleep 3
shot 05_group_detail_two_expenses.png

echo "==> Back to groups list"
tap_el "Back"; sleep 2
shot 06_groups_list_with_data.png

echo "==> Settings"
tap_el "Settings"; sleep 2
shot 07_settings.png

echo "Done. Screenshots in $OUT/"
ls -la "$OUT/"