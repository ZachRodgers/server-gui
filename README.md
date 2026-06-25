# Minecraft Server Console Wrapper

`server-gui` is a desktop wrapper for an existing Minecraft server. Drop it into a server folder, launch it once, and it detects the server jar, builds the launch command, and writes `server-wrapper.properties`.

![Mock UI screenshot](docs/images/mock-ui.png)

## Install

1. Copy `server-gui-0.3.0.jar` from `target/` into your server folder.
2. Run it once:

```bash
java -Dsun.awt.X11.XWMClass=com-servergui-Main -jar server-gui-0.3.0.jar
```

First launch detects the server jar, writes `server-wrapper.properties`, and creates `start.sh` and `start.bat`. After that, just run:

```bash
./start.sh
```

Generated `start.sh`:

```bash
#!/bin/bash
cd "$(dirname "$0")"
JAR="$(ls server-gui*.jar 2>/dev/null | sort -V | tail -n 1)"
if [ -z "$JAR" ]; then
  echo "server-gui jar not found."
  exit 1
fi
nohup java -Dsun.awt.X11.XWMClass=com-servergui-Main -jar "$JAR" &>/dev/null &
```

The script picks the newest `server-gui` jar, so leftover old versions are ignored.

## What It Does

- wraps a Paper, Purpur, Spigot, or similarly named server jar
- auto-detects the server jar on first launch, and re-detects it if the configured jar is renamed or updated
- launches the server with `nogui`
- shows live logs, filters, command input, player list, TPS, and memory
- mock mode when no server jar is found
- syncs a git repo from in-game chat (see Git Sync)

## Usage

On first launch the wrapper looks for a server jar in the same folder (`paper-*.jar`, `purpur-*.jar`, `spigot-*.jar`, `server.jar`). If found, it fills in `server.command` and disables mock mode. If not, it stays in mock mode so you can test the UI safely.

To run without the GUI (for headless or remote servers), add `-nogui`:

```bash
java -jar server-gui-0.3.0.jar -nogui
```

With `-nogui` the wrapper launches the detected server jar, streams its output to the terminal, and forwards typed input to it, just like running the server jar directly. The GUI, polling, and player views are skipped; only the `!git` chat commands run in the background. Point an existing server CLI or wrapper at `server-gui.jar -nogui` in place of the paper jar and it keeps working as before, with git sync added.

## Configuration

Typical generated config:

```properties
app.title=Minecraft Server Console
mock.mode=false
server.command=java -Xms3G -Xmx6G -jar "paper-1.21.10-130.jar" nogui
working.directory=.
poll.players.seconds=30
poll.tps.seconds=30
poll.heap.seconds=20
```

Notes:

- keep `nogui` in `server.command`
- heap changes need a server restart
- player/TPS polling works best on Paper-compatible servers
- memory uses `jcmd` against the launched Java process

## Git Sync

If you keep a datapack (or similar) in a git repo, the wrapper can pull updates from in-game chat. No console or plugins needed.

In the Git Sync settings tab, enable it and pick the repo folder (the one containing `.git`). After that an opped player can type:

- `!git pull` pulls the active branch and reloads
- `!git pull <branch>` switches to that branch and pulls it
- `!git pull -silent` pulls without posting status in chat
- `!git version [n]` shows the branch and the latest commit (or n commits, 1-20)
- `!git help` lists these commands

Notes:

- non-ops are ignored
- pulls are rate-limited to 3 every 90 seconds
- status, changed files, and commit info post to chat; full output goes to the console
- `git pull` must work without prompts (public repo or cached credentials)

## Editing

Edit settings from the app instead of the config file when you can. Direct edits are fine to rename the window, change polling intervals, adjust `-Xms`/`-Xmx`, or point at a different launch command.

## Contributing

- keep changes focused and commits small
- preserve user files and existing server folders
- test with both mock mode and a real server jar
- commit source, resources, and README changes; keep packaged jars out of normal commits
