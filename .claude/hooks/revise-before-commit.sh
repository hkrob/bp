#!/usr/bin/env bash
# PreToolUse hook (Bash matcher). Forces a claude-md-management:revise-claude-md
# pass before the first git commit of each "round": denies once (so the model
# runs the skill with real session context), then allows the retry.
set -euo pipefail

input="$(cat)"
cmd="$(python3 -c "import json,sys; print(json.load(sys.stdin).get('tool_input', {}).get('command', ''))" <<<"$input")"

# Only care about an actual `commit` subcommand, not `git log`, `git status`,
# nor a `-c user.name=... commit` message body that happens to say "commit".
if ! printf '%s' "$cmd" | grep -Eq '(^|[;&|[:space:]])git(\s+-c\s+\S+)*\s+commit(\s|$)'; then
  echo '{"continue": true}'
  exit 0
fi

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
marker="$repo_root/.git/claude-md-reviewed"

if [ -f "$marker" ]; then
  rm -f "$marker"
  echo '{"continue": true}'
else
  touch "$marker"
  cat <<'JSON'
{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny", "permissionDecisionReason": "Run the claude-md-management:revise-claude-md skill first (review this session for learnings and update CLAUDE.md if warranted), then retry this exact commit."}}
JSON
fi
