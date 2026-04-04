# Minecraft Server GUI

A standalone Java desktop wrapper for a Minecraft server process. It hides the stock server window and exposes a simpler operator view:

- live console with search and severity filters
- command input
- player list
- TPS chart
- Java heap chart for the child server process
- mock mode for testing the UI without a real server jar
- Minecraft-inspired window chrome, widgets, and icon
- player head thumbnails in the online player list

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

If no supported server jar is detected on first run, the app stays in mock mode and generates fake log/player/TPS/heap activity so you can test the UI safely.

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
- The current builds use a safe local font fallback for text rendering stability. The Minecraft-style look currently comes from the textures, layout, and icon rather than a bundled in-game font renderer.
- Player faces are fetched from `https://mineskin.eu/helm/<PLAYERNAME>` for the online player list.

## Current state

This is a functional desktop wrapper with the main architecture in place. Areas still worth refining:

- final polish on the Minecraft-style theme
- richer event parsing
- server profiles
- per-world/player stats
- RCON support if you want metrics without command spam
