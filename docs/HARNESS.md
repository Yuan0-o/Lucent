# The agent harness

Lucent's assistant is not limited to notes and tasks. Behind **Settings → Agent toolkit** there is a
tool gateway, and everything the assistant does passes through it: a permission check, an approval
policy, and an entry in the activity log.

## How a call is handled

```
model asks for a tool
      ↓
tool registry      the name must belong to an enabled group and a platform that supports it
      ↓
permission policy  READ WRITE DELETE EXECUTE NETWORK GIT GITHUB BROWSER SENSITIVE DEVICE
      ↓                each set to allow, ask or block
approval           "ask" raises the confirmation dialog, with every arguable field editable
      ↓
the tool runs      inside the workspace, with a timeout and an output cap
      ↓
audit log          tool, arguments (shortened), outcome, duration
```

## Tool groups

| Group | What it covers |
|---|---|
| Files | list, search, read, write, edit with a diff, copy, move, delete, zip, metadata, snapshots and rollback |
| Terminal | commands with a working directory and timeout, background jobs, output polling, tool probing |
| Documents | Word, Excel and PowerPoint written and read natively; LibreOffice conversion and rendering when the plugin is installed |
| PDF | text and metadata, search, page rendering as images, merge and split |
| Web | page fetching with readable text and links, file download, search, opening links |
| Data | SQLite queries and schema, CSV import and export, column analysis |
| Git | status, diff, log, blame, branch, checkout, add, commit, restore, stash, merge, rebase, patch, fetch, pull, push |
| GitHub | repositories, contents, issues, pull requests, branches, commits, search, actions and CI logs, releases |
| Sandbox | run a command in Docker, in PRoot, or directly, with limits reported |
| Memory | user, project and session memories, plus workspace notes |
| Planning | a published plan with per-step status |
| Sub-agents | background helpers with their own context |
| Skills | instruction files discovered in the workspace |
| MCP | any Model Context Protocol server you add, its tools appearing as `mcp__server__tool` |
| Plugins | the catalogue, mirror speed tests, install, remove and run |
| Device (Android) | screen reading, tap, type, swipe, screenshots, apps, notifications, clipboard, sensors, torch |
| Connectors | Notion, Slack, Google Drive, OneDrive, GitLab, Jira, Linear, WebDAV and a general HTTP request |

## Where things live

- **Workspace** — the one folder the assistant may read and write. Files outside it are refused; the
  "outside the workspace" permission decides whether reading further afield is even offered.
- **Snapshots** — every file is copied aside before it is changed, so `restore_file` can undo a
  mistake. Snapshots are capped and encrypted at rest.
- **Activity log** — the audit trail, readable and clearable from the same settings page.
- **Plugins** — heavy tooling (a Linux userland, Python, LibreOffice, Node.js, browsers, OCR, media
  tools) is never bundled. It is downloaded on request from the project's own servers or a fast
  mirror, whichever answers first, with size and checksum verification.
- **Secrets** — API keys, the GitHub token, MCP tokens and connector credentials are encrypted with
  the same local key as the rest of Lucent's data, and are included in `.lcb` backups.
