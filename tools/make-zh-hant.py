# Generates Traditional Chinese strings (Taiwan wording) from the Simplified ones.
# Usage: python tools/make-zh-hant.py      (needs: pip install opencc-python-reimplemented)
# values-zh/*.xml  ->  values-b+zh+Hant/*.xml ; run again after changing Simplified strings.
import pathlib
import opencc

res = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
src = res / "values-zh"
dst = res / "values-b+zh+Hant"
dst.mkdir(exist_ok=True)
cc = opencc.OpenCC("s2twp")

# Words OpenCC (s2twp) turns into something people in Taiwan / Hong Kong would not say for this app.
FIX = {
    "記賬": "記帳",
    "轉賬": "轉帳",
    "賬單": "帳單",
    "賬本": "帳本",
    "賬戶": "帳戶",
    "對賬": "對帳",
    "入賬": "入帳",
    "到賬": "到帳",
    "賬": "帳",
    "後臺": "背景",
    "後台": "背景",
}

for f in sorted(src.glob("*.xml")):
    text = cc.convert(f.read_text(encoding="utf-8"))
    for a, b in FIX.items():
        text = text.replace(a, b)
    (dst / f.name).write_text(text, encoding="utf-8", newline="\n")
    print("wrote", (dst / f.name).relative_to(res))
