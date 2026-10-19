#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 [--dry-run] (pr <number> | untagged)" >&2
  exit 2
}

dry_run=false
if [[ ${1:-} == --dry-run ]]; then
  dry_run=true
  shift
fi

case "${1:-}:$#" in
  pr:2) [[ $2 =~ ^[0-9]+$ ]] || usage; target="pr-$2" ;;
  untagged:1) target="" ;;
  *) usage ;;
esac

owner=yagipass

cd "$(dirname "$0")/../.."
err=$(mktemp)
trap 'rm -f "$err"' EXIT

for dockerfile in examples/*/Dockerfile; do
  example=${dockerfile#examples/}
  package=verbatime-example-${example%/Dockerfile}
  if ! versions=$(gh api --paginate "/users/$owner/packages/container/$package/versions?per_page=100" \
    --jq '.[] | [.id, (.metadata.container.tags | join(","))] | @tsv' 2>"$err"); then
    if grep -q 'HTTP 404' "$err"; then
      echo "$package: no such package"
      continue
    fi
    cat "$err" >&2
    exit 1
  fi
  [[ -n $versions ]] || continue
  remaining=$(grep -c . <<<"$versions")
  while IFS=$'\t' read -r id tags; do
    [[ $tags == "$target" ]] || continue
    if ((remaining == 1)); then
      echo "::warning::$package: keeping version $id [$tags], the last version of the package"
      continue
    fi
    if $dry_run; then
      echo "$package: would delete version $id [$tags]"
    else
      gh api -X DELETE "/users/$owner/packages/container/$package/versions/$id" --silent
      echo "$package: deleted version $id [$tags]"
    fi
    remaining=$((remaining - 1))
  done <<<"$versions"
done
