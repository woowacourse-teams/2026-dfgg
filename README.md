# dfgg

**Item build recommendations for the game you're actually in.**

Live service: https://www.dfgg.pro/

dfgg recommends League of Legends item builds based on the specific ten champions in your
match — not on a fixed, statistics-averaged build path.

---

## The problem

About 36% of players on the Korean server sit between Iron and Silver. Our research found
two recurring problems for this group:

1. **Keeping up with the meta is hard.** Item nerfs and buffs land every patch, and the
   build a player memorized quietly stops being correct without them noticing.
2. **Item knowledge is thin.** Players know a build order, but not _why_ — so they cannot
   adapt it when the enemy team is full of healing, crowd control, or armor.

Existing services (OP.GG, GGQ, LOL.PS) answer this with statistics-based, fixed item paths —
essentially "what most players bought." We tested all three in live games across the jungle,
mid, and support positions, and found the same gaps in every one:

| Gap                  | What we saw                                                                                    |
| -------------------- | ---------------------------------------------------------------------------------------------- |
| Guidance stops early | Recommendations end at the 2nd–3rd core item, with only an unranked list of options after that |
| No reasoning         | The build is shown, but never _why_ this item against _this_ enemy team                        |
| Position blind spots | Support items are omitted entirely; jungle pathing constraints are ignored                     |
| Information overload | So much on screen that champion select ends before anything useful is found                    |

Every one of these services stops at _showing data_. None of them get to _judging the
situation_. That gap is what dfgg is built for.

---

## What dfgg does

### 1. Enter the champion select manually

Fill in all ten champions — five allies, five enemies — pick your own position, and get an
item order built against that exact composition.

The engine aggregates builds from high-mastery, high-win-rate players on your champion within
comparable rank brackets, then filters and re-orders them against the enemy team's crowd
control density, healing and shielding, AD/AP damage split, and tank count.

![Home](/image.png)

### 2. Desktop app (Windows)

The desktop app does the same job without typing anything in.

- **In-game overlay** — reads the ten champions from the League client's local Live Client
  Data API and shows the recommended item order in a small, movable overlay. It can be
  collapsed at any time and never sends input to the game.
- **Home window** — your own profile, current rank and recent match history, read from the
  local League client.
- **Highlights** — while you play, dfgg records the League game window on your PC and cuts
  short clips around your own kills, assists and objectives. After the game a highlight page
  shows the result together with the clips.
  - Recording, encoding and storage happen **only on your PC**. Videos are never uploaded.
  - Only the most recent 20 games are kept; the raw recording is deleted once clips are made.
  - Recording is skipped when free disk space is low.
  - Uninstalling the app removes all recordings and app data.

---

## Roadmap

- [v] Recommendations past the third core item, including late-game situational swaps
- [v] A one-line, plain-language rationale attached to every recommended item
- [ ] Position-specific handling — support items, jungle timing
- [v] Domain and HTTPS for the production deployment
- [v] Desktop overlay, match history and local game highlights
- [ ] Highlight on/off setting and recording notice

---

## Project structure

```
dfgg/
├── frontend/
│   └── apps/
│       └── desktop/   # Electron app (overlay, home, highlights)
└── backend/
```

## Getting started

```bash
# frontend
cd frontend

# backend
cd backend
```

## Tech stack

- **Frontend**: React, TypeScript, Webpack
- **Backend**: Java, Spring Boot, PostgreSQL
- **Desktop**: Electron, React, TypeScript, FFmpeg (LGPLv3, Windows Graphics Capture)
- **Infrastructure**: AWS EC2
- **Analytics**: Umami, PostHog (production builds only)
- **Data**: Riot Games API (ACCOUNT-V1, SUMMONER-V4, LEAGUE-V4, MATCH-V5,
  CHAMPION-MASTERY-V4, SPECTATOR-V5) and Data Dragon for static champion and item data

### Local endpoints used by the desktop app (read-only)

| Source | Endpoint | Used for |
| --- | --- | --- |
| LCU | `/lol-gameflow/v1/gameflow-phase` (+ websocket event) | Detect lobby / in-game / end of game |
| LCU | `/lol-gameflow/v1/session` | `gameId` and queue to link highlights to a match |
| LCU | `/lol-summoner/v1/current-summoner` (+ `/summoner-profile`) | Your own profile |
| LCU | `/lol-ranked/v1/current-ranked-stats` | Your own rank |
| LCU | `/lol-match-history/v1/products/lol/current-summoner/matches`, `/lol-match-history/v1/games/{gameId}` | Your match history |
| LCU | `/lol-end-of-game/v1/eog-stats-block` | Post-game result page |
| LCU | `/lol-game-queues/v1/queues`, `/lol-patch/v1/game-version` | Queue names, patch version |
| Live Client | `/liveclientdata/playerlist`, `/liveclientdata/activeplayer` | Champions in the game, overlay recommendations |
| Live Client | `/liveclientdata/eventdata`, `/liveclientdata/gamestats` | Timing your own kills/assists/objectives for clips |

dfgg only reads these endpoints. It never writes to the client and never automates any
action.

### Third-party notice

The desktop app bundles an unmodified FFmpeg build (BtbN, LGPLv3, no GPL components). The
license text and source link ship with the app in `resources/ffmpeg/LICENSE.txt` and
`resources/ffmpeg/NOTICE.txt`.

---

## About

dfgg is built by a four-person team as part of [Woowacourse](https://woowacourse.github.io/),
a non-profit software engineering education program run by Woowa Brothers in South Korea.
The service is free to use.

dfgg presents only information a player can already see in champion select and on the
in-game scoreboard. It does not surface any game-session-specific information that would
otherwise be unknown to the player. Highlights are recorded from the player's own screen and
stay on the player's PC.

dfgg is not endorsed by Riot Games and does not reflect the views or opinions of Riot Games
or anyone officially involved in producing or managing Riot Games properties. Riot Games and
all associated properties are trademarks or registered trademarks of Riot Games, Inc.
