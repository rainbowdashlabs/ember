#!/usr/bin/env python3
"""
Vue Template Refactoring Tool

Zero-dependency tokenizer for Vue templates.
Parses opening/closing tags preserving case, extracts classes,
and performs safe line-by-line replacements.

Usage:
    python3 scripts/refactor.py scan [--min N]
    python3 scripts/refactor.py preview --match Tag.class1.class2 --tag NewTag [--props 'key="val"'] [--import path]
    python3 scripts/refactor.py replace --match Tag.class1.class2 --tag NewTag [--props 'key="val"'] [--import path]
"""

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

SRC_DIR = Path(__file__).resolve().parent.parent / "src"

# ── Vue file helpers ─────────────────────────────────────────────────


def extract_template(content: str) -> tuple[str, int] | None:
    start = content.find("<template>")
    end = content.rfind("</template>")
    if start == -1 or end == -1:
        return None
    return content[start : end + len("</template>")], content[:start].count("\n") + 1


def extract_script(content: str) -> str | None:
    m = re.search(r"<script[^>]*>(.*?)</script>", content, re.DOTALL)
    return m.group(1) if m else None


def rel_path(p: Path) -> str:
    return str(p.relative_to(SRC_DIR.parent))


def is_component_dir(p: Path) -> bool:
    try:
        p.relative_to(SRC_DIR / "components")
        return True
    except ValueError:
        return False


def walk_vue():
    yield from sorted(SRC_DIR.rglob("*.vue"))


# ── Tokenizer ────────────────────────────────────────────────────────

# Matches opening tags preserving Vue component case: <MyComponent ...>
OPEN_TAG = re.compile(r"<([A-Za-z][\w-]*)((?:\s[^>]*?)?)\s*(/?)>")
CLASS_ATTR = re.compile(r'\bclass="([^"]*)"')

LAYOUT_PREFIXES = (
    "flex", "inline-flex", "grid", "gap-", "items-", "justify-",
    "self-", "place-", "col-span", "row-span",
    "sm:", "md:", "lg:", "xl:",
    "space-x-", "space-y-", "order-", "grow", "shrink", "basis-",
)


def parse_classes(attrs: str) -> list[str]:
    m = CLASS_ATTR.search(attrs)
    return m.group(1).split() if m else []


def is_layout_only(classes: list[str]) -> bool:
    return all(any(c.startswith(p) for p in LAYOUT_PREFIXES) for c in classes)


# ── Scan ─────────────────────────────────────────────────────────────


def scan(min_count=5, skip_components=True, skip_layout=True):
    patterns = defaultdict(list)

    for path in walk_vue():
        if skip_components and is_component_dir(path):
            continue
        content = path.read_text()
        result = extract_template(content)
        if not result:
            continue
        template, base_line = result

        for m in OPEN_TAG.finditer(template):
            tag, attrs = m.group(1), m.group(2)
            classes = parse_classes(attrs)
            if len(classes) < 3:
                continue
            if skip_layout and is_layout_only(classes):
                continue
            line = base_line + template[: m.start()].count("\n")
            patterns[(tag, tuple(sorted(classes)))].append((path, line))

    results = [(k, v) for k, v in patterns.items() if len(v) >= min_count]
    results.sort(key=lambda x: -len(x[1]))
    return results


# ── Replace ──────────────────────────────────────────────────────────


def add_import(script: str, component: str, path: str) -> str:
    if component in script:
        return script
    imp = f"import {component} from '{path}'"
    lines = script.split("\n")
    last = max((i for i, l in enumerate(lines) if l.strip().startswith("import ")), default=-1)
    lines.insert(last + 1 if last >= 0 else 0, imp)
    return "\n".join(lines)


def replace_in_file(content, match_tag, match_classes, new_tag, new_props="", import_path=None):
    lines = content.split("\n")
    out = []
    count = 0
    pending_closes = 0

    for line in lines:
        # Try opening tag match
        if f"<{match_tag}" in line:
            cm = CLASS_ATTR.search(line)
            if cm:
                found = set(cm.group(1).split())
                if match_classes.issubset(found):
                    remaining = sorted(found - match_classes)
                    # Replace tag name
                    new_line = re.sub(rf"<{re.escape(match_tag)}(\s|>|/>)", rf"<{new_tag}\1", line, count=1)
                    # Handle classes
                    if remaining:
                        new_line = CLASS_ATTR.sub(f'class="{" ".join(remaining)}"', new_line, count=1)
                    else:
                        new_line = re.sub(r'\s*class="[^"]*"', "", new_line, count=1)
                    # Add props
                    if new_props:
                        new_line = re.sub(rf"<{re.escape(new_tag)}(\s|>)", rf"<{new_tag} {new_props}\1", new_line, count=1)
                    # Handle same-line closing tag
                    close = f"</{match_tag}>"
                    if close in new_line:
                        new_line = new_line.replace(close, f"</{new_tag}>", 1)
                    elif "/>" not in line:
                        pending_closes += 1
                    out.append(new_line)
                    count += 1
                    continue

        # Try closing tag
        close = f"</{match_tag}>"
        if pending_closes > 0 and close in line:
            line = line.replace(close, f"</{new_tag}>", 1)
            pending_closes -= 1

        out.append(line)

    result = "\n".join(out)

    if import_path and count > 0:
        script = extract_script(result)
        if script:
            new_script = add_import(script, new_tag, import_path)
            if new_script != script:
                result = result.replace(script, new_script, 1)

    return result, count


# ── CLI ──────────────────────────────────────────────────────────────


def cmd_scan(args):
    results = scan(min_count=args.min, skip_components=not args.include_components)
    if not results:
        print("\nNo repeated patterns found.\n")
        return
    total = sum(len(v) for _, v in results)
    print(f"\n{'='*70}\nFound {len(results)} patterns ({total} occurrences)\n{'='*70}\n")
    for (tag, classes), locs in results:
        files = len(set(rel_path(f) for f, _ in locs))
        print(f"  {len(locs):3d}x in {files:2d} files: <{tag} class=\"{' '.join(classes)}\">")
        print(f"       --match '{tag}.{'.'.join(classes)}'")
        print()


def cmd_replace(args):
    parts = args.match.split(".")
    tag, classes = parts[0], set(parts[1:])
    if not tag:
        sys.exit("Error: selector must start with a tag name")
    dry = getattr(args, "dry_run", False)
    total = changed = 0
    for path in walk_vue():
        content = path.read_text()
        new, n = replace_in_file(content, tag, classes, args.tag, args.props or "", args.import_path)
        if n:
            total += n; changed += 1
            print(f"  {'[preview]' if dry else '[updated]'} {rel_path(path)}: {n}")
            if not dry:
                path.write_text(new)
    print(f"\n{'Would replace' if dry else 'Replaced'} {total} in {changed} file(s).\n")


def main():
    p = argparse.ArgumentParser(description="Vue Template Refactoring Tool")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("scan")
    s.add_argument("--min", type=int, default=5)
    s.add_argument("--include-components", action="store_true")
    s.set_defaults(func=cmd_scan)

    for name, dry in [("replace", False), ("preview", True)]:
        r = sub.add_parser(name)
        r.add_argument("--match", required=True)
        r.add_argument("--tag", required=True)
        r.add_argument("--props", default="")
        r.add_argument("--import", dest="import_path")
        r.set_defaults(func=cmd_replace, dry_run=dry)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
