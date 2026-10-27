# Gaming Community Discord Bot

A Java bot for a gaming community, built on the Discord API (JDA) with
SQLite storage — one database per server. It covers the full feature
list: tournaments, teams and players, leaderboards and match results,
moderation, and announcements and reminders.

## Features

### A. Tournament management
- `/tournament-create` with game, format, date, team limit, and solo play
- Register with `/register-solo` or the Register button on the creation
  message. The Register button only appears on solo tournaments; team
  tournaments tell you to join with `/team-create` and `/team-join`
  instead
- Registration closes automatically when the team limit is reached
- `/bracket` generates pairings and a schedule
- `/announce-match` posts match details in the channel

### B. Team and player management
- `/team-create` (creator becomes captain), `/team-invite`, `/team-join`
- One team per player per tournament is enforced
- Solo registration for tournaments that allow it
- Player profiles with wins, losses, points, and match history
  (`/player-leaderboard`, with a page option for long lists)

### C. Leaderboards and match results
- `/submit-result` creates a pending result; the other side confirms with
  `/confirm-result` or disputes with `/dispute-result`
- Moderators fix results with `/correct-result`, which recalculates rankings
- `/leaderboard` (teams) and `/player-leaderboard` (players)
- A win is worth 3 points

### D. Moderation
- `/warn`, `/mute`, `/kick`, `/ban`, `/timeout`, `/clear`
- Every action and reason is logged to the server's database (`/mod-log`)
- Discord role permissions are checked before each action
- 5-second per-command cooldowns stop command spam

### E. Announcements and reminders
- `/announce-winner`, `/announce-match`, `/remind-captains`
- `/remind` for a timed reminder in the channel
- `/notify-opt` lets members turn matches, results, or announcements on/off

### F. Utility
- `/help` lists every command, grouped by category
- `/status` shows uptime, server count, tournament count, and team count
- When the bot joins a server, it posts a welcome message pointing at
  `/help` and `/tournament-create`

## Example run

1. Create a tournament:
   `/tournament-create name: Friday Cup game: Valorant format: Single elimination`
2. Teams form: one captain runs `/team-create tournament: Friday Cup
   team: Alpha`, others join with `/team-join`. (Or players register
   solo if the tournament allows it.)
3. Generate the bracket: `/bracket tournament: Friday Cup`. Round 1
   pairings appear as scheduled matches.
4. After a match, the winning captain reports it:
   `/submit-result tournament: Friday Cup winner: Alpha loser: Beta`.
   The losing captain confirms with `/confirm-result`, and only then
   are the 3 points applied. A disputed result stays pending for
   moderators instead of changing the leaderboard.
5. Winners advance automatically: once every match in a round is
   confirmed, the next round's pairings are created, until one team
   remains as champion.
6. Check standings anytime with `/leaderboard`, `/player-leaderboard`,
   or the `/dashboard` panel.

## Setup

1. Create an application at https://discord.com/developers/applications,
   add a bot, and copy its token.
2. Invite the bot with the `bot` and `applications.commands` scopes, using
   least privilege (not Administrator):
   https://discord.com/oauth2/authorize?client_id=1564287699198681128&permissions=1101927640070&scope=bot%20applications.commands
3. Set the token as an environment variable (never commit it):

```bash
export DISCORD_TOKEN="your-bot-token-here"
```

4. Build and run (requires Java 17 and Maven):

```bash
mvn package
java -jar target/gaming-discord-bot-1.0.jar
```

Data lives in SQLite, one database per server
(`data/<server-id>/bot.db`), so servers never see each other's
tournaments, teams, or moderation logs. A backup copy of all data is
made on every bot start. A JSON mirror of each server's tournaments is
kept next to its database for the web dashboard (see below).

## Stand-out features

- **Real bracket engine:** `/bracket` supports single elimination
  (default), double elimination, and round-robin formats, with byes
  for odd team counts. Single elimination advances confirmed winners
  round by round until a champion is decided. Double elimination runs
  a real winners bracket and losers bracket, then a final — and if the
  losers-bracket champion wins the first final, a reset match decides
  it. Round-robin creates the full schedule as real match records.
  `/withdraw` removes a team (captain or moderator only): its pending
  bracket matches are voided, its opponents advance, and registration
  reopens if slots free up.
- **Match verification:** only the winning team's captain can submit a
  result (with an optional score). Only the opposing team's captain can
  confirm it. No points are applied until confirmation. Disputes are
  logged for moderators instead of updating the leaderboard.
- **Per-server data:** every server gets its own manager and database.
  Tournaments, teams, player profiles, and moderation logs are scoped
  to the server they were created in.
- **Game roles:** `/game-role` assigns a Valorant, BGMI, Minecraft, or CS2
  role, replacing any previous game role. The bot needs Manage Roles and
  a role above the game roles.
- **Player statistics:** `/player-stats` shows matches played, win rate,
  tournament wins, and recent form. These statistics come from matches
  recorded by this bot, not from a verified game-data source.
- **In-Discord dashboard:** `/dashboard` shows an embed panel with every
  tournament, team counts, registration status, top 3 teams, and recent
  matches, plus a bot thumbnail and timestamp. Buttons switch between
  Tournaments and Leaderboard views, or Refresh the panel.
- **Web admin dashboard:** see `dashboard/` below.

## Web admin dashboard (Spring Boot + Vue)

The dashboard is a small Spring Boot app with a Vue frontend that reads
the bot's per-server JSON mirror, so it always shows the same
tournaments as the bot.

```bash
cd dashboard
mvn spring-boot:run
```

Then open http://localhost:8080. From there you can list tournaments,
create one, and view leaderboards without typing Discord commands.
The dashboard finds the bot's data folder whether you start it from the
project root or from `dashboard/`. Restart the bot to pick up
tournaments created in the dashboard, since the bot loads its data at
startup.

Dashboard login: set `DASHBOARD_ADMIN_TOKEN` before starting the
dashboard, then open it as
`http://localhost:8080/?token=<your token>`. Without the token set, the
dashboard is open (development only).

## Security notes

- Invite the bot with least privilege, not Administrator:
  https://discord.com/oauth2/authorize?client_id=1564287699198681128&permissions=1101927640070&scope=bot%20applications.commands
- Data lives in SQLite, one database per server (`data/<server-id>/bot.db`),
  and is copied to `backups/` on every bot start.
- Web dashboard login: set `DASHBOARD_ADMIN_TOKEN` before starting the
  dashboard, then open it as `http://localhost:8080/?token=<your token>`.
  Without the token set, the dashboard is open (development only).
- Hosting is intentionally not set up here yet: it comes last, once
  everything is verified working.
