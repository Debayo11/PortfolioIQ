#!/usr/bin/env python3
"""
Static cross-check of the Flyway migrations against the JPA entities.

Hibernate runs with ddl-auto=validate, so a mismatch (a missing column, a CHAR column mapped to a String, a typo in a
table name) stops the application at startup. This script finds those mismatches without needing a database, so it
can run in CI and on a laptop. It is a safety net, not a replacement for the Testcontainers integration tests, which
run the real migrations on a real PostgreSQL.

Usage: python3 scripts/check_schema.py        (exit code 1 on any error)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS = sorted((ROOT / "backend/src/main/resources/db/migration").glob("V*.sql"))
JAVA = ROOT / "backend/src/main/java"

errors, warnings = [], []

# ---------------------------------------------------------------- SQL parsing


def strip_sql(text):
    text = re.sub(r"--[^\n]*", "", text)
    return re.sub(r"\$\$.*?\$\$", "$$BODY$$", text, flags=re.S)  # DO blocks are checked separately


def split_top_level(s, sep=","):
    parts, depth, cur = [], 0, ""
    for ch in s:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == sep and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def base_type(t):
    t = t.strip().lower()
    t = re.sub(r"\(.*?\)", "", t)
    return re.sub(r"\s+", " ", t).strip()


TABLES = {}   # name -> {column: type}
FKS = []      # (table, referenced table, referenced column)
INDEXES = []  # (name, table, raw column list)

for f in MIGRATIONS:
    sql = strip_sql(f.read_text())
    if sql.count("(") != sql.count(")"):
        errors.append(f"{f.name}: unbalanced parentheses")
    for stmt in [s.strip() for s in sql.split(";") if s.strip()]:
        m = re.match(r"CREATE TABLE\s+(\w+)\s*\((.*)\)\s*$", stmt, re.S | re.I)
        if m:
            name, body = m.group(1), m.group(2)
            cols = {}
            for item in split_top_level(body):
                item = item.strip()
                if re.match(r"(CONSTRAINT|PRIMARY KEY|UNIQUE|CHECK|FOREIGN KEY)\b", item, re.I):
                    continue
                cm = re.match(r"(\w+)\s+([A-Za-z]+(?:\s*\([\d,\s]+\))?(?:\s+WITH(?:OUT)? TIME ZONE)?)", item)
                if not cm:
                    errors.append(f"{f.name}: cannot parse column definition in {name}: {item[:60]!r}")
                    continue
                cols[cm.group(1).lower()] = base_type(cm.group(2))
                for r in re.finditer(r"REFERENCES\s+(\w+)\s*\((\w+)\)", item, re.I):
                    FKS.append((name, r.group(1).lower(), r.group(2).lower()))
            TABLES[name.lower()] = cols
            continue
        m = re.match(r"ALTER TABLE\s+(\w+)\s+ADD COLUMN\s+(\w+)\s+([A-Za-z]+(?:\s*\([\d,\s]+\))?)", stmt, re.I)
        if m:
            TABLES.setdefault(m.group(1).lower(), {})[m.group(2).lower()] = base_type(m.group(3))
            continue
        m = re.match(r"ALTER TABLE\s+(\w+)\s+ALTER COLUMN\s+(\w+)\s+TYPE\s+([A-Za-z]+(?:\s*\([\d,\s]+\))?)", stmt, re.I)
        if m:
            t, c = m.group(1).lower(), m.group(2).lower()
            if t not in TABLES or c not in TABLES[t]:
                errors.append(f"{f.name}: ALTER COLUMN on unknown column {t}.{c}")
            else:
                TABLES[t][c] = base_type(m.group(3))
            continue
        m = re.match(r"CREATE\s+(?:UNIQUE\s+)?INDEX\s+(\w+)\s+ON\s+(\w+)\s*\((.*?)\)", stmt, re.S | re.I)
        if m:
            INDEXES.append((m.group(1), m.group(2).lower(), m.group(3)))

for t, rt, rc in FKS:
    if rt not in TABLES:
        errors.append(f"foreign key in {t} references unknown table {rt}")
    elif rc not in TABLES[rt]:
        errors.append(f"foreign key in {t} references unknown column {rt}.{rc}")

for name, t, cols in INDEXES:
    if t not in TABLES:
        errors.append(f"index {name} is on unknown table {t}")
        continue
    for c in split_top_level(cols):
        c = c.strip().split()[0]
        if re.fullmatch(r"\w+", c) and c.lower() not in TABLES[t]:
            errors.append(f"index {name} uses unknown column {t}.{c}")

# --------------------------------------------------------------- entity parsing

FAMILIES = {
    "String": {"varchar", "character varying", "text"},
    "Long": {"bigint", "bigserial"}, "long": {"bigint", "bigserial"},
    "Short": {"smallint", "smallserial"}, "Integer": {"integer", "int", "serial"}, "int": {"integer", "int", "serial"},
    "BigDecimal": {"numeric", "decimal"},
    "Boolean": {"boolean"}, "boolean": {"boolean"},
    "Instant": {"timestamptz", "timestamp with time zone"},
    "LocalDate": {"date"}, "UUID": {"uuid"},
}


def snake(name):
    return re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", name).lower()


def annotation_arg(text, ann, arg):
    m = re.search(rf"@{ann}\s*\((.*?)\)\s*$", text, re.S | re.M) or re.search(rf"@{ann}\s*\(([^@]*?)\)", text, re.S)
    if not m:
        return None
    a = re.search(rf'\b{arg}\s*=\s*"([^"]+)"', m.group(1))
    return a.group(1) if a else None


entities = 0
checked = 0
for jf in sorted(JAVA.rglob("*.java")):
    src = jf.read_text()
    if "@Entity" not in src:
        continue
    entities += 1
    cls = jf.stem
    tm = re.search(r'@Table\s*\(\s*name\s*=\s*"(\w+)"', src)
    if not tm:
        errors.append(f"{cls}: @Entity without @Table(name=...)")
        continue
    table = tm.group(1).lower()
    if table not in TABLES:
        errors.append(f"{cls}: table {table} does not exist in the migrations")
        continue
    cols = TABLES[table]

    # every field: annotations followed by "private <Type> name"
    for fm in re.finditer(r"((?:\s*@[\w.]+(?:\([^;{}]*?\))?\s*)*)\s*private\s+(?!static)([\w<>,.? ]+?)\s+(\w+)\s*(?:=[^;]*)?;", src, re.S):
        anns, jtype, fname = fm.group(1), fm.group(2).strip(), fm.group(3)
        if "@Transient" in anns:
            continue
        if "@ManyToMany" in anns:
            jt = re.search(r'@JoinTable\s*\(\s*name\s*=\s*"(\w+)".*?joinColumns\s*=\s*@JoinColumn\s*\(\s*name\s*=\s*"(\w+)"\s*\).*?inverseJoinColumns\s*=\s*@JoinColumn\s*\(\s*name\s*=\s*"(\w+)"', anns, re.S)
            if not jt:
                errors.append(f"{cls}.{fname}: cannot read @JoinTable")
            else:
                for c in (jt.group(2), jt.group(3)):
                    if jt.group(1) not in TABLES or c not in TABLES[jt.group(1)]:
                        errors.append(f"{cls}.{fname}: join table {jt.group(1)} lacks column {c}")
            continue
        if "@ElementCollection" in anns:
            ct = re.search(r'@CollectionTable\s*\(\s*name\s*=\s*"(\w+)".*?joinColumns\s*=\s*@JoinColumn\s*\(\s*name\s*=\s*"(\w+)"', anns, re.S)
            cc = re.search(r'@Column\s*\([^)]*name\s*=\s*"(\w+)"', anns)
            if not ct or not cc:
                errors.append(f"{cls}.{fname}: cannot read element collection mapping")
            else:
                for c in (ct.group(2), cc.group(1)):
                    if ct.group(1) not in TABLES or c not in TABLES[ct.group(1)]:
                        errors.append(f"{cls}.{fname}: collection table {ct.group(1)} lacks column {c}")
            continue

        cn = re.search(r'@Column\s*\([^)]*?\bname\s*=\s*"(\w+)"', anns)
        column = cn.group(1).lower() if cn else snake(fname)
        if column not in cols:
            errors.append(f"{cls}.{fname}: column {table}.{column} does not exist")
            continue
        sql_type = cols[column]
        checked += 1
        if jtype in FAMILIES:
            ok = sql_type in FAMILIES[jtype]
        elif re.fullmatch(r"[A-Z]\w+", jtype):          # an enum stored as text
            ok = "@Enumerated" in anns and sql_type in FAMILIES["String"]
        else:
            ok = True
        if not ok:
            errors.append(f"{cls}.{fname}: Java type {jtype} does not match SQL type '{sql_type}' of {table}.{column}"
                          + (" (CHAR columns are not accepted by Hibernate validation for String)" if sql_type in ("char", "bpchar") else ""))

print(f"Migrations: {len(MIGRATIONS)}   tables: {len(TABLES)}   entities: {entities}   mapped columns verified: {checked}")
for w in warnings:
    print("WARN ", w)
for e in errors:
    print("ERROR", e)
print("RESULT:", "FAILED" if errors else "OK")
sys.exit(1 if errors else 0)
