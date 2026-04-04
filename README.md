# Minecraft Server GUI

A standalone Java desktop wrapper for a Minecraft server process. It hides the stock server window and exposes a simpler operator view:

- live console with search and severity filters
- command input
- player list
- TPS chart
- Java heap chart for the child server process

## Run

```bash
mvn package
```

## Simplest Setup

1. Copy [server-gui-0.1.0.jar](/home/Zach/MineCraft/Repos/server-gui/target/server-gui-0.1.0.jar) into the same folder as your Paper/Spigot server jar.
2. Run:

```bash
java -jar server-gui-0.1.0.jar
```

On first run the app writes `server-wrapper.properties` in that folder.

If it sees a jar like `paper-1.21.10-130.jar`, `spigot-...jar`, or `server.jar`, it now auto-fills the launch command and turns off mock mode for you.

Only edit the config if you want to change memory or point at a different startup command.

## Example Config

```properties
app.title=Minecraft Server Console
mock.mode=false
server.command=java -Xms3G -Xmx6G -jar "paper-1.21.10-130.jar" nogui
working.directory=.
poll.players.seconds=10
poll.tps.seconds=15
poll.heap.seconds=10
```

Notes:

- Keep `nogui` in `server.command` so the stock Minecraft UI does not appear.
- `list` and `tps` polling work best on Paper or compatible servers.
- Heap metrics use `jcmd` against the launched Java child process.
- If no server jar is detected on first run, the app stays in mock mode until you edit `server-wrapper.properties`.

## Current state

This is a functional starter repo, not a finished product. The main architecture is in place and ready for refinement:

- better theme and icons
- richer event parsing
- server profiles
- per-world/player stats
- RCON support if you want metrics without command spam
