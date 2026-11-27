#!/bin/zsh
set -e
cd "$(dirname "$0")/../.."
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
tmp=$(mktemp -d)
img() { echo "<img src=\"file://$PWD/$1\">"; }
if [[ $1 == hero ]]; then
  sed -e "s#SHOT1#file://$PWD/$2#" -e "s#SHOT2#file://$PWD/$3#" -e "s#SHOT3#file://$PWD/$4#" -e "s#LOGO#file://$PWD/docs/logo/icon.svg#" docs/mockups/hero.html > "$tmp/p.html"
  out=hero; size=1600,900
else
  out=$2; size=1600,1000
  python3 - "$tmp/p.html" "$3" "$4" "$5" "$(img $6)" "$7" "$(img $8)" "$9" "$(img ${10})" "${11}" <<'PY'
import sys
out, accent, title, sub, p1, c1, p2, c2, p3, c3 = sys.argv[1:]
import os
s = open(os.environ.get('TEMPLATE', 'docs/mockups/trio.html')).read()
for k, v in dict(ACCENT=accent, SUBTITLE=sub, TITLE=title, PH1=p1, CAP1=c1, PH2=p2, CAP2=c2, PH3=p3, CAP3=c3).items():
    s = s.replace(k, v)
open(out, 'w').write(s)
PY
fi
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=$size --screenshot="$PWD/docs/images/$out.png" "file://$tmp/p.html" >/dev/null 2>&1
echo "docs/images/$out.png"
