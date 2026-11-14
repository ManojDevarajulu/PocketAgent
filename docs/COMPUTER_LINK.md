# Computer Link

Lets an MCP app on **your own computer** (Claude Code, Cursor, and others) hand a task to your phone. It is off by
default and works over your local Wi-Fi only: there is no cloud relay and no account.

## Turn it on

1. On the phone: **Settings > Computer link > Let my computer send tasks**. The phone shows its address, for example
   `192.168.1.20:8765`. Tap the address to copy the full URL (`http://192.168.1.20:8765/mcp`).
2. Tap **Copy connect command** and paste it into a terminal on your computer. For Claude Code it looks like:

   ```bash
   claude mcp add --transport http pocket-agent http://192.168.1.20:8765/mcp \
     --header "Authorization: Bearer <your pairing token>"
   ```

   The token is made on the phone the first time you switch the link on. The copied command contains it, so the
   phone marks the clipboard entry as sensitive. Treat the command like a password.
3. Ask your computer's agent to do something on the phone. A card on the phone shows the exact words and waits for
   **Allow**.

## What the computer can do

One tool, `run_task`. The phone's owner approves each request on the phone, then the normal agent runs it with all
the usual protections: blocked apps, confirmation before anything that sends, calls or pays, and the step limit.

The computer **cannot** read the screen, list apps, tap, type, or see notifications by itself. It only sees the short
text the task ends with.

The phone refuses (and says why) when it is locked, already busy, recording a routine, or has no AI provider set up.

## How it is locked down

- Off until you switch it on. It listens only while the switch is on.
- A random 192-bit token is required on every request. Five wrong tokens lock the link for a minute.
- Only callers on your private network (or the phone itself) are served. Anything else is dropped.
- Requests that carry an `Origin` header, which is what a web page sends, are refused.
- Requests are capped at 16 KB and have read timeouts. A task that runs over 5 minutes returns a timeout; it keeps
  running on the phone until it ends or you stop it.
- It makes no outgoing connections. The `bridge` package is the only one that may listen, and
  `ArchitectureTest` checks that.

## Limits

- Plain HTTP on a trusted network: anyone who can capture traffic on your Wi-Fi could read the token. Use a network
  you trust. If you think the token leaked, tap **Reset pairing code**; every connected computer stops working until
  you copy the new connect command.
- The phone's Wi-Fi must allow device-to-device traffic. Some guest networks block it.
- Android can stop background apps on some phones. If the link stops answering, open Pocket Agent once.

## Try it without Claude Code

```bash
curl -X POST http://192.168.1.20:8765/mcp \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```
