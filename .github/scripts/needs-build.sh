#!/usr/bin/env bash
# Reads changed file paths (one per line) on stdin and prints "true" if any of
# them can affect the app or its build, "false" if the change is docs-only.
# An empty list prints "true": when in doubt, build.
#
# Docs-only means: Markdown files, the license, and GitHub issue forms.
# Everything else (code, Gradle, resources, schemas, workflows, scripts) builds.
set -u
seen=0
while IFS= read -r f; do
  [ -z "$f" ] && continue
  seen=1
  case "$f" in
    *.md | LICENSE | .github/ISSUE_TEMPLATE/*) ;;
    *)
      echo "Needs a build: $f" >&2
      echo true
      exit 0
      ;;
  esac
done
if [ "$seen" -eq 0 ]; then echo true; else echo false; fi
