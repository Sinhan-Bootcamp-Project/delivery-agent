#!/usr/bin/env bash
# PR에서 바뀐 services/*/api/openapi.yaml을 base 브랜치와 비교해
# 깨지는 변경이 있으면 실패한다. JSON Schema는 아직 형식 검사만 한다.
set -euo pipefail

base="${1:-origin/develop}"
status=0

changed=$(git diff --name-only "$base"...HEAD -- 'services/**/api/*' || true)
if [ -z "$changed" ]; then
  echo "No contract changes."
  exit 0
fi

for file in $changed; do
  [ -f "$file" ] || { echo "Removed contract: $file"; status=1; continue; }
  case "$file" in
    *.yaml|*.yml)
      if git cat-file -e "$base:$file" 2>/dev/null; then
        git show "$base:$file" > /tmp/base-contract.yaml
        echo "Checking $file"
        oasdiff breaking /tmp/base-contract.yaml "$file" --fail-on ERR || status=1
      else
        echo "New contract: $file"
      fi
      ;;
    *.json)
      python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$file" || status=1
      ;;
  esac
done

exit $status
