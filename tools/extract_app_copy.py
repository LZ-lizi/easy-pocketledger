"""Collect the app's on-screen copy into one document that a person can edit.

Every string the app shows is written inline in Kotlin, which is fine for the code and
useless for anyone who wants to reword it. This walks the sources and picks out the copy.

Which literals count is decided by **what the file is**, not by guessing at the call site,
because a rule that reads the surrounding code is one that quietly includes a JSON key one
week and quietly drops a dialog title the next:

* A **screen, dialog or UI component** is copy. Every Chinese literal in one is kept, except
  the few that exist only for a stack trace (`require`, `check`, `Log`).
* A **view model or mixed domain file** is mostly machinery, so only a literal handed to
  something that renders -- `text =`, `title =`, `message =`, or an enum constant's own
  label such as `WECHAT("微信支付账单")` -- is kept.
* The **matcher tables** (merchant keyword -> category, CSV column aliases, status words)
  are pattern data rather than copy, so they are reported and not extracted.

Output, next to this file:

* `记账本文案.xlsx` -- four sheets: the interface copy to edit, the text inside exported
  files, how to edit, and what was deliberately left out.
* `app-copy.json`   -- the same rows with the exact source file and line, so an edited
  document can be applied back to the code mechanically.

Run:  python tools/extract_app_copy.py
"""

import json
import os
import re
import sys
from collections import OrderedDict

import openpyxl
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
SOURCE_ROOT = os.path.join(PROJECT, "app", "src", "main", "java")
RES_ROOT = os.path.join(PROJECT, "app", "src", "main", "res")
MANIFEST = os.path.join(PROJECT, "app", "src", "main", "AndroidManifest.xml")
XLSX_PATH = os.path.join(HERE, "记账本文案.xlsx")
JSON_PATH = os.path.join(HERE, "app-copy.json")

CJK = re.compile(r"[\u4e00-\u9fff]")

# --------------------------------------------------------------- the file policy

# Every Chinese literal in these is copy the user can read.
COPY_FILES = (
    "feature/entry/EntryViewModel.kt",   # the 支出 / 收入 / 转账 / 月付 selector
    "data/Presets.kt",                   # category, account and plan names
    "data/InstallmentRunner.kt",         # the plan name it writes
    "data/BudgetAlertChecker.kt",        # the alert it raises
    "data/backup/BackupService.kt",      # why a backup was refused
    "domain/QuickCategories.kt",         # the entry page's default buttons
    "domain/DateLabels.kt",
    "domain/Money.kt",
    "domain/DateKeys.kt",
)
COPY_PREFIXES = (
    "feature/", "ui/components/", "ui/navigation/", "ui/util/", "widget/", "notify/",
)

# Written into a file the user opens rather than drawn on a screen, so it is offered
# separately -- changing a CSV column name also changes what the importer has to accept.
OPTIONAL_FILES = {
    "domain/CsvExport.kt":
        "导出的 CSV 文件里的表头。改了之后，本应用再导入自己导出的 CSV 时需要同步更新识别规则。",
    "domain/AppBackup.kt":
        "备份文件内部的结构名与文件名。通常不需要改。",
}

# Pattern data, never shown. Reported in the last sheet rather than extracted.
PATTERN_FILES = {
    "domain/CategoryMatcher.kt":
        "导入时用来猜类别的商户关键词表（美团→外卖 这种匹配规则，界面上不显示）",
    "domain/XlsxReader.kt": "xlsx 内部解析",
    "data/LedgerDatabase.kt": "数据库定义（SQL）",
    "di/AppContainer.kt": "依赖注入",
    "di/AppContainerAccess.kt": "依赖注入",
}

# Machinery that happens to contain Chinese.
NEVER_SHOWN = (
    ("日志", re.compile(r"\b(Log\.[vdiew]|println|System\.(out|err))")),
    ("内部断言", re.compile(r"\b(require|check|checkNotNull|requireNotNull)\s*\(")),
)

# A literal that reaches a screen from a logic file sits on the right of one of these.
RENDERS = re.compile(
    r"(?:\b(?:text|title|subtitle|label|placeholder|supportingText|contentDescription|"
    r"headline|message|msg|error|errorMessage|status|statusMessage|hint|summary|"
    r"description|note|detail|name|names|items|options|labels|lines|paragraphs|"
    r"bullets|steps|tips|entries|caption|body|footer|unit|prefix|suffix|confirmText|"
    r"dismissText|actionLabel|positiveText|negativeText)\s*=\s*)$"
    r"|(?:\bText\s*\(\s*text\s*=\s*)$",
)

# An enum constant carrying its own label: `WECHAT("微信支付账单"),`.
ENUM_CONSTANT = re.compile(r"^\s*[A-Z][A-Z0-9_]*\s*\(")

# The same sinks, but only the ones that are unambiguously a drawing call. Used to judge a
# literal with no Chinese in it, where a field name is no evidence at all.
STRONG_SINKS = re.compile(
    r"(?:\bText\s*\(\s*(?:text\s*=\s*)?)$"
    r"|(?:\b(text|contentDescription|placeholder|supportingText|label|title|headline|"
    r"subtitle)\s*=\s*)$")

# Tables that only exist to recognise something: accepted column names, the words a bill
# uses for 支出, the merchants that mean 外卖. Named by convention, so a table added later
# is caught by its name rather than by someone remembering to list it here.
PATTERN_VAL = re.compile(
    r"^[ \t]*(?:private |internal |public )?(?:val|var)\s+"
    r"(\w*(?:NAMES|WORDS|STATUSES|TOKENS|MARKERS|TYPES|ALIASES|KEYWORDS|PATTERNS|RULES|"
    r"SYNONYMS|PREFIXES|SUFFIXES|HEADERS))\s*[:=]")
VAL_DECLARATION = re.compile(
    r"^[ \t]*(?:private |internal |public |const )*(?:val|var)\s+(\w+)\s*[:=]", re.M)

DECLARATION = re.compile(
    r"^[ \t]*(?:@\w+(?:\([^)]*\))?[ \t]*\n[ \t]*)*"
    r"(?:public |internal |private |protected |open |abstract |override |suspend |inline |"
    r"operator |tailrec |const |lateinit |actual |expect |external )*"
    r"(fun|val|var|class|object|interface)\s+([A-Za-z_][\w]*)", re.M)

FUN = re.compile(
    r"^[ \t]*(?:@\w+(?:\([^)]*\))?[ \t]*\n[ \t]*)*"
    r"(?:public |internal |private |protected |open |abstract |override |suspend |inline |"
    r"operator |tailrec |const |actual |expect |external )*fun\s+([A-Za-z_][\w]*)", re.M)

AREAS = OrderedDict([
    ("feature/home", "首页 / 记账"),
    ("feature/entry", "记账页"),
    ("feature/edit", "编辑账单"),
    ("feature/detail", "账单详情"),
    ("feature/search", "搜索"),
    ("feature/stats", "统计"),
    ("feature/accounts", "账户"),
    ("feature/budget", "预算"),
    ("feature/installments", "月付 / 分期"),
    ("feature/categories", "类别管理"),
    ("feature/imports", "导入账单"),
    ("feature/export", "导出与备份"),
    ("feature/settings", "设置"),
    ("feature/onboarding", "首次启动引导"),
    ("ui/components", "通用界面组件"),
    ("ui/navigation", "底部导航"),
    ("ui/util", "日期显示"),
    ("notify/", "系统通知"),
    ("widget/", "桌面小组件"),
    ("data/Presets.kt", "预置数据（首次安装写入数据库）"),
    ("data/InstallmentRunner.kt", "月付自动扣款"),
    ("data/BudgetAlertChecker.kt", "预算提醒"),
    ("data/backup/", "备份与恢复"),
    ("domain/AppBackup.kt", "备份文件"),
    ("domain/Money.kt", "金额格式"),
    ("domain/CsvImport.kt", "导入账单"),
    ("domain/QuickCategories.kt", "记账页常用类别"),
    ("domain/DateLabels.kt", "日期显示"),
    ("domain/DateKeys.kt", "日期显示"),
    ("feature/", "其他界面"),
])

# What a string is doing where it is, from the sink it sits in.
SCENES = [
    (re.compile(r"\bcontentDescription\s*=\s*$"), "无障碍朗读文字"),
    (re.compile(r"\bplaceholder\s*=\s*$"), "输入框里的灰色提示"),
    (re.compile(r"\bsupportingText\s*=\s*$"), "输入框下方的说明"),
    (re.compile(r"\b(confirmText|positiveText)\s*=\s*$"), "确认按钮"),
    (re.compile(r"\b(dismissText|negativeText)\s*=\s*$"), "取消按钮"),
    (re.compile(r"\blabel\s*=\s*$"), "标签 / 选项卡"),
    (re.compile(r"\b(headline|title|subtitle)\w*\s*=\s*$"), "标题"),
    (re.compile(r"\b(message|msg|error|errorMessage|status)\w*\s*=\s*$"), "提示消息"),
    (re.compile(r"\b(names|items|options|labels|lines|paragraphs|bullets|steps|tips|"
                r"entries)\s*=\s*$"), "列表内容"),
    (re.compile(r"\b(text|body|caption|note|summary|description|unit|prefix|suffix)\s*=\s*$"),
     "界面文字"),
    (re.compile(r"\bText\s*\(\s*text\s*=\s*$"), "界面文字"),
]


def area_of(relative):
    for prefix, area in AREAS.items():
        if relative.startswith(prefix):
            return area
    return relative.split("/")[0]


def source_relative(path):
    """`.../java/com/pocketledger/feature/home/HomeScreen.kt` -> `feature/home/HomeScreen.kt`."""
    relative = os.path.relpath(path, SOURCE_ROOT).replace("\\", "/")
    parts = relative.split("/")
    if "com" in parts:
        parts = parts[parts.index("com") + 2:] if len(parts) > parts.index("com") + 1 \
            else parts
    return "/".join(parts)


def scene_of(before, line_text):
    if ENUM_CONSTANT.match(line_text):
        return "选项名称"
    for pattern, scene in SCENES:
        if pattern.search(before):
            return scene
    return "界面文字"


# ------------------------------------------------------------------- reading Kotlin


def skip_brace_block(text, i):
    """`i` is just past an opening `{`; return the index just past its match.

    Brace counting alone is not enough: a template expression can hold a string of its own
    (`${x.ifBlank { "未知" }}`), and that string can hold braces. Both are walked here.
    """
    depth, n = 1, len(text)
    while i < n:
        ch = text[i]
        if ch == '"':
            i = skip_string(text, i, 0)
        elif ch == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
            i += 1
        elif ch == "{":
            depth += 1
            i += 1
        elif ch == "}":
            depth -= 1
            i += 1
            if depth == 0:
                return i
        else:
            i += 1
    return n


def skip_string(text, i, _unused):
    """`i` is at an opening quote; return the index just past the closing quote."""
    n = len(text)
    j = i + 1
    while j < n:
        if text[j] == "\\":
            j += 2
        elif text[j] == "$" and j + 1 < n and text[j + 1] == "{":
            j = skip_brace_block(text, j + 2)
        elif text[j] == '"':
            return j + 1
        else:
            j += 1
    return n


def literals_in(text):
    """(start, end, characters) for every string literal, comments and chars skipped."""
    found = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if text.startswith("//", i):
            i = text.find("\n", i)
            if i < 0:
                break
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
        elif ch == "'":
            # A character literal, including '\'' and the '"' that once broke the scan.
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
            i += 1
        elif text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end < 0 else end
            found.append((i, end + 3, text[i + 3:end]))
            i = end + 3
        elif ch == '"':
            end = skip_string(text, i, 0)
            found.append((i, end, text[i + 1:end - 1]))
            i = end
        else:
            i += 1
    return found


# A sentence assembled with `+`: `"额度是同一个，" + "在任意一边改都会同步。"`. Shown as two
# fragments it reads as nonsense, so the pieces are joined before anything else looks at them.
JOIN = re.compile(r"\s*\+\s*(.*?)\s*\+\s*", re.S)
GLUE = re.compile(r"\s*\+\s*")


def join_chains(text, literals):
    """Group literals that are `+`-ed together, returning (first, last, parts) runs."""
    runs, current = [], []
    for index, (start, end, raw) in enumerate(literals):
        if current:
            _, previous_end, _ = literals[current[-1]]
            gap = text[previous_end:start]
            match = JOIN.fullmatch(gap)
            if match:
                current.append(index)
                continue
            if GLUE.fullmatch(gap):
                current.append(index)
                continue
            runs.append(current)
            current = []
        current.append(index)
    if current:
        runs.append(current)
    return runs


def chain_text(text, literals, run):
    """The whole sentence a `+` chain spells out, with each expression as a `{marker}`."""
    pieces = []
    for position, index in enumerate(run):
        start, end, raw = literals[index]
        pieces.append(unescape(raw))
        if position + 1 < len(run):
            nxt = literals[run[position + 1]][0]
            match = JOIN.fullmatch(text[end:nxt])
            if match and match.group(1).strip():
                pieces.append("{%s}" % match.group(1).strip())
    return "".join(pieces)


ESCAPES = {"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$", "'": "'", "r": "\r"}


def unescape(raw):
    out, i = [], 0
    while i < len(raw):
        if raw[i] == "\\" and i + 1 < len(raw):
            out.append(ESCAPES.get(raw[i + 1], raw[i + 1]))
            i += 2
        else:
            out.append(raw[i])
            i += 1
    return "".join(out)


def with_marks(text):
    """Kotlin's `$name` / `${expr}` shown as an editable `{name}` marker.

    Returns the display text and, for each marker, which of the two forms it came from.
    The form has to be remembered: putting `${` back in front of a plain `$count` would
    still compile, but it would change how the string is read and written by anyone who
    opens the file next.

    The braces are balanced by hand rather than by a regular expression: an expression
    placeholder routinely contains braces of its own (`${x.ifBlank { "?" }}`), and a
    non-greedy pattern stops inside it and leaves the rest of the sentence mangled.
    """
    out, marks, i, n = [], {}, 0, len(text)
    while i < n:
        ch = text[i]
        if ch == "$" and i + 1 < n and text[i + 1] == "{":
            depth, j = 0, i + 1
            while j < n:
                if text[j] == "{":
                    depth += 1
                elif text[j] == "}":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            name = text[i + 2:j].strip()
            marks[name] = "braced"
            out.append("{%s}" % name)
            i = j + 1
        elif ch == "$" and i + 1 < n and (text[i + 1].isalpha() or text[i + 1] == "_"):
            j = i + 1
            while j < n and (text[j].isalnum() or text[j] in "_."):
                j += 1
            name = text[i + 1:j]
            marks[name] = "simple"
            out.append("{%s}" % name)
            i = j
        else:
            out.append(ch)
            i += 1
    return "".join(out), marks


def pattern_table_at(text, offset):
    """The name of the recognition table a literal sits in, if it sits in one."""
    head = text[:offset]
    names = [match for match in PATTERN_VAL.finditer(head)]
    if not names:
        return None
    nearest = names[-1]
    # Only a table that has not been left behind: a later `val` means we are past it.
    later = [match for match in VAL_DECLARATION.finditer(head[nearest.end():])]
    return nearest.group(1) if not later else None


def fun_blocks(text):
    """(start, end, name) for each function body, so a position can name its screen."""
    blocks = []
    for match in FUN.finditer(text):
        brace = text.find("{", match.end())
        if brace < 0:
            continue
        depth, i, n = 0, brace, len(text)
        while i < n:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        blocks.append((match.start(), i, match.group(1)))
    return blocks


def where_of(blocks, offset, text):
    best = None
    for start, end, name in blocks:
        if start <= offset <= end:
            if best is None or start > best[0]:
                best = (start, name)
    if best:
        return best[1]
    head = text[:offset]
    names = [match.group(2) for match in DECLARATION.finditer(head)]
    return names[-1] if names else None


def prettify(name):
    """`FeatureGuideScreen` / `entryTypeLabel` -> something readable."""
    if not name:
        return ""
    spaced = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", name)
    return spaced[:1].upper() + spaced[1:]


def line_of(text, offset):
    return text[:offset].count("\n") + 1


def is_copy_file(relative):
    if relative in PATTERN_FILES:
        return False
    if relative in COPY_FILES:
        return True
    return any(relative.startswith(prefix) for prefix in COPY_PREFIXES)


# ------------------------------------------------------------------------ collecting


def collect():
    kept, dropped, optional = [], [], []

    for folder, _, names in os.walk(RES_ROOT):
        for name in sorted(names):
            if not name.endswith(".xml"):
                continue
            full = os.path.join(folder, name)
            relative = "res/" + os.path.relpath(full, RES_ROOT).replace("\\", "/")
            text = open(full, encoding="utf-8").read()
            for match in re.finditer(
                    r'<string[^>]*name="(\w+)"[^>]*>(.*?)</string>', text, re.S):
                body = re.sub(r"\s+", " ", match.group(2)).strip()
                key = match.group(1)
                area = ("桌面小组件" if key.startswith("widget")
                        else "应用名称" if key == "app_name" else "资源文件")
                kept.append({
                    "file": relative, "line": line_of(text, match.start()), "area": area,
                    "scene": "应用名称" if key == "app_name" else "桌面小组件文字",
                    "text": body, "raw": body, "where": key, "source": "res",
                    "id": key, "marks": [],
                })

    if os.path.exists(MANIFEST):
        text = open(MANIFEST, encoding="utf-8").read()
        for match in re.finditer(r'android:label="([^"]*)"', text):
            if match.group(1).startswith("@"):
                continue
            kept.append({
                "file": "res/AndroidManifest.xml", "line": line_of(text, match.start()),
                "area": "应用名称", "scene": "机型桌面上的名称", "text": match.group(1),
                "raw": match.group(1), "where": "application", "source": "manifest",
                "id": None, "marks": [],
            })

    for folder, _, names in os.walk(SOURCE_ROOT):
        for name in sorted(names):
            if not name.endswith(".kt"):
                continue
            full = os.path.join(folder, name)
            relative = source_relative(full)
            text = open(full, encoding="utf-8").read()
            copy_file = is_copy_file(relative)
            blocks = fun_blocks(text)

            if relative in PATTERN_FILES or relative in OPTIONAL_FILES:
                bucket = optional if relative in OPTIONAL_FILES else dropped
                why = OPTIONAL_FILES.get(relative) or PATTERN_FILES[relative]
                literals = literals_in(text)
                for run in join_chains(text, literals):
                    start, _, _ = literals[run[0]]
                    plain = chain_text(text, literals, run)
                    if CJK.search(plain):
                        bucket.append({
                            "file": relative, "line": line_of(text, start),
                            "area": area_of(relative), "scene": "文件内容",
                            "where": prettify(where_of(fun_blocks(text), start, text)),
                            "text": None, "raw": plain, "why": why})
                continue

            for match in re.finditer(r"stringResource\(\s*R\.string\.(\w+)", text):
                kept.append({
                    "file": relative, "line": line_of(text, match.start()),
                    "area": area_of(relative), "scene": "资源引用",
                    "text": "@string/" + match.group(1), "raw": None,
                    "where": where_of(blocks, match.start(), text), "source": "kt-resource",
                    "id": match.group(1), "marks": [],
                })

            literals = literals_in(text)
            for run in join_chains(text, literals):
                start, end, raw = literals[run[0]]
                plain = chain_text(text, literals, run)
                line_start = text.rfind("\n", 0, start) + 1
                line_text = text[line_start:].split("\n")[0]
                before = text[max(0, start - 200):start]

                reason = None
                for label, pattern in NEVER_SHOWN:
                    if pattern.search(line_text):
                        reason = label
                        break
                if reason is None:
                    table = pattern_table_at(text, start)
                    if table:
                        reason = f"识别用的对照表 {table}（用来认列/认商户，界面上不显示）"

                renders = bool(RENDERS.search(before)) or bool(ENUM_CONSTANT.match(line_text))
                if not (CJK.search(plain) or renders):
                    continue
                # Without Chinese to go on, only a literal that is plainly being drawn
                # counts -- otherwise `name = "settings"` on a DataStore looks like copy.
                if not CJK.search(plain) and not (
                        STRONG_SINKS.search(before) or ENUM_CONSTANT.match(line_text)):
                    continue

                if reason:
                    dropped.append({"file": relative, "line": line_of(text, start),
                                    "text": plain, "why": reason})
                    continue

                if copy_file or renders:
                    kept.append({
                        "file": relative, "line": line_of(text, start),
                        "start": start, "end": literals[run[-1]][1],
                        "area": area_of(relative), "scene": scene_of(before, line_text),
                        "text": None, "raw": plain,
                        "where": prettify(where_of(blocks, start, text)),
                        "source": "kt", "id": None,
                    })
                else:
                    dropped.append({"file": relative, "line": line_of(text, start),
                                    "text": plain,
                                    "why": "代码内部字符串，不显示在界面上"})

    for row in kept:
        if row["source"] in ("res", "manifest") or row["text"]:
            continue
        row["text"], row["marks"] = with_marks(row["raw"])
    for row in optional:
        if not row["text"]:
            row["text"], row["marks"] = with_marks(row["raw"])
    return kept, dropped, optional


AREA_ORDER = [
    "应用名称", "首次启动引导", "底部导航", "首页 / 记账", "记账页", "编辑账单",
    "账单详情", "搜索", "统计", "账户", "预算", "月付 / 分期", "类别管理",
    "导入账单", "导出与备份", "设置", "通用界面组件", "日期显示", "金额格式",
    "系统通知", "桌面小组件", "预置数据（首次安装写入数据库）", "备份与恢复",
    "月付自动扣款", "预算提醒",
]

HEAD_FILL = PatternFill("solid", fgColor="1F3864")
HEAD_FONT = Font(color="FFFFFF", bold=True, size=11)
EDIT_FILL = PatternFill("solid", fgColor="FFF2CC")
BAND_FILL = PatternFill("solid", fgColor="F2F5FA")
THIN = Side(style="thin", color="D9D9D9")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)

HOW_TO_EDIT = [
    ("这份文档是什么",
     "记账本 App 里每一句会显示给你看的文字，都在这里。改「修改为」那一列就行，"
     "没动的行保持空白即可。改完把文件发回来，我会把改动逐条替换进程序。"),
    ("列的意思",
     "序号：用来说「第 37 条改成……」的编号，别改。\n"
     "界面：这句话出现在哪个页面。\n"
     "位置：页面里的哪一块（按钮、标题、输入框提示等）。\n"
     "类型：这句话的作用（按钮文字、提示消息、选项名称……）。\n"
     "当前文字：现在显示的内容。\n"
     "修改为：留给你的，填你想改成的话。"),
    ("花括号 { } 是占位符，别删也别改",
     "形如 {Money.formatWithSymbol(alert.spentCents)} 或 {count} 的部分，"
     "是程序运行时填进去的数字或名称，不是固定文字。可以移动它在句子里的位置，"
     "但不能删掉、不能改里面的字，否则那处会显示不出来。\n"
     "例：「已花 {金额}，超出 {上限}」可以改成「本月已用 {金额}，超出预算 {上限}」。"),
    ("同一个词在多处出现",
     "同一个写法只会有一行，后面「界面」列会列出它出现的所有地方。"
     "如果你希望某一处单独改（比如两个「取消」要不一样），在「修改为」里写上"
     "「只改〇〇页的那处」即可。"),
    ("预置的类别名、账户名",
     "这些名字是在第一次安装时写进数据库的。改这份文档里的它们，"
     "只会影响以后新装的设备；已经装好并记过账的手机上，类别名要改请在 App 里"
     "「设置 → 类别管理」中改。"),
    ("没有列进来的文字",
     "注释、日志、SQL、以及导入时用来猜类别的商户关键词表（美团→外卖 这种）"
     "不会显示给用户，所以没放进来。它们都记在「未列入」那一页，"
     "如果你也想改，告诉我一声。"),
]

__doc_notes__ = None


def write_workbook(rows, optional, dropped):
    book = openpyxl.Workbook()

    def sheet(title, columns, widths, records, note=None, band_by=None):
        page = book.create_sheet(title)
        row_at = 1
        if note:
            page.cell(row=1, column=1, value=note).font = Font(italic=True, color="7F7F7F")
            page.merge_cells(start_row=1, start_column=1,
                             end_row=1, end_column=len(columns))
            row_at = 3
        for column, (header, width) in enumerate(zip(columns, widths), start=1):
            cell = page.cell(row=row_at, column=column, value=header)
            cell.fill, cell.font = HEAD_FILL, HEAD_FONT
            cell.alignment = Alignment(horizontal="center", vertical="center")
            page.column_dimensions[get_column_letter(column)].width = width
        page.row_dimensions[row_at].height = 22
        page.freeze_panes = page.cell(row=row_at + 1, column=1)

        previous = None
        for offset, record in enumerate(records):
            line = row_at + 1 + offset
            for column, value in enumerate(record, start=1):
                cell = page.cell(row=line, column=column, value=value)
                cell.border = BORDER
                cell.alignment = Alignment(
                    vertical="top", wrap_text=column >= 2,
                    horizontal="center" if column == 1 else "left")
                if band_by and band_by(record) != previous:
                    cell.fill = BAND_FILL
            page.cell(row=line, column=len(columns)).fill = EDIT_FILL
            if band_by:
                previous = band_by(record)
        page.auto_filter.ref = (
            f"A{row_at}:{get_column_letter(len(columns))}"
            f"{row_at + len(records)}")
        return page

    def interface_records(source):
        ordered = sorted(source, key=lambda r: (
            AREA_ORDER.index(r["area"]) if r["area"] in AREA_ORDER else len(AREA_ORDER),
            r["where"] or "", r["text"]))
        out = []
        for number, row in enumerate(ordered, start=1):
            row["number"] = number
            out.append((number, row["area"], row["where"] or "", row["scene"],
                        row["text"], None))
        return ordered, out

    ordered, records = interface_records(rows)
    sheet("界面文字", ["序号", "界面", "位置", "类型", "当前文字（原文）", "修改为"],
          [6, 22, 24, 16, 62, 62], records,
          note="只填「修改为」那一列；不确定的留空。花括号 { } 里的内容是变量，不要删改。",
          band_by=lambda r: r[1])

    optional_ordered, optional_records = interface_records(optional)
    sheet("导出文件里的文字",
          ["序号", "所在文件", "位置", "类型", "当前文字（原文）", "修改为"],
          [6, 22, 24, 16, 62, 62],
          [(n, r["file"], r["where"] or "", r["scene"], r["text"], None)
           for (n, _, _, _, _, _), r in zip(optional_records, optional_ordered)],
          note="这些字出现在「导出的文件」里，不在 App 界面上。默认不用改；"
               "改了 CSV 表头的话，请一并告诉我，我需要同步更新导入时的识别规则。",
          band_by=lambda r: r[1])

    how = book.create_sheet("说明")
    how.column_dimensions["A"].width = 26
    how.column_dimensions["B"].width = 96
    how.cell(row=1, column=1, value="怎么用这份文档").font = Font(bold=True, size=14)
    for offset, (title, body) in enumerate(HOW_TO_EDIT, start=3):
        head = how.cell(row=offset, column=1, value=title)
        head.font = Font(bold=True)
        head.alignment = Alignment(vertical="top", wrap_text=True)
        cell = how.cell(row=offset, column=2, value=body)
        cell.alignment = Alignment(vertical="top", wrap_text=True)
        how.row_dimensions[offset].height = 16 * (body.count("\n") + 1 + len(body) // 60)
    how.cell(row=len(HOW_TO_EDIT) + 5, column=1,
             value=f"共 {len(records)} 条界面文字，{len(optional_records)} 条导出文件文字。"
             ).font = Font(italic=True, color="7F7F7F")

    left = None
    sheet_rows = [(r["file"], r["line"], r["text"], r["why"]) for r in dropped]
    sheet("未列入", ["文件", "行号", "文字", "为什么没列进来"],
          [34, 8, 46, 56], sheet_rows,
          note="这些是代码内部的文字，不显示给用户，所以没有放进「界面文字」。"
               "如果其中也有你想改的，告诉我。")

    book.remove(book["Sheet"])
    book.save(XLSX_PATH)
    return len(records), len(optional_records)


def main():
    kept, dropped, optional = collect()

    # The same wording in five places is one row to edit, with every place listed.
    grouped = OrderedDict()
    for row in kept:
        key = row["text"]
        if key not in grouped:
            row["places"] = []
            row["occurrences"] = 0
            row["locations"] = []
            grouped[key] = row
        entry = grouped[key]
        entry["occurrences"] += 1
        if row["source"] == "kt":
            entry["locations"].append({
                "file": row["file"], "line": row["line"],
                "start": row["start"], "end": row["end"],
                "raw": row["raw"], "marks": row["marks"],
            })
        place = f"{row['area']} · {row['where']}" if row["where"] else row["area"]
        if place not in entry["places"]:
            entry["places"].append(place)
    rows = list(grouped.values())
    for row in rows:
        # The row already says which screen it starts on, so only the *other* screens need
        # naming here; otherwise the first entry just repeats the column to its left.
        places = []
        for place in row["places"]:
            area, _, element = place.partition(" · ")
            if area == row["area"] or not element:
                places.append(element or area)
            else:
                places.append(f"{area} · {element}")
        row["where"] = "；".join(places[:3]) + (
            f" 等{len(places)}处" if len(places) > 3 else "")

    print(f"kept {len(kept)} literals -> {len(rows)} distinct strings; "
          f"exported-file {len(optional)}; left out {len(dropped)}")
    # The workbook assigns the 序号 the editor quotes back, so it has to be built first and
    # the JSON written after, or the sidecar has no numbers in it.
    interface, exported = write_workbook(rows, optional, dropped)
    with open(JSON_PATH, "w", encoding="utf-8", newline="\n") as handle:
        json.dump({"rows": rows, "optional": optional, "dropped": dropped},
                  handle, ensure_ascii=False, indent=1)
    print(f"wrote {XLSX_PATH}: {interface} interface strings, {exported} exported-file")
    print("wrote", JSON_PATH)


if __name__ == "__main__":
    sys.exit(main())
