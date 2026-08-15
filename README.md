# dfgg

**Item build recommendations for the game you're actually in.**

Live service: [dfgg.pro](https://www.dfgg.pro/)

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

### 2. Let the desktop app fill it in for you (in development)
Skip the typing. The desktop client reads the ten champions straight from your client and sends the recommendation to an overlay on top of the game.

During champion select it reads the League Client session, so you get a build before the game even loads. Once the match starts it switches to Riot's Live Client Data API. That second source is what makes normal games work — in blind pick the enemy team stays hidden through champion select, so reading the live match is the only way to know what you are actually up against. Draft queues get the recommendation early; every other queue gets it the moment the game begins.

The overlay sits in the corner of the game, updates as picks land, and clicks pass straight through it so it never gets in the way of your own champion select. Nothing about your client leaves your machine — only champion names and positions are sent to our server. The app does not read game memory, automate input, or modify the game in any way.

<img width="413" height="889" alt="image" src="https://github.com/user-attachments/assets/18d0483d-2898-496d-b6ab-614761e35f81" />

---

## Roadmap

- [v] Recommendations past the third core item, including late-game situational swaps
- [v] A one-line, plain-language rationale attached to every recommended item
- [ ] Position-specific handling — support items, jungle timing
- [ ] Domain and HTTPS for the production deployment

---

## Project structure

```
dfgg/
├── frontend/
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
- **Infrastructure**: AWS EC2
- **Data**: Riot Games API (ACCOUNT-V1, SUMMONER-V4, LEAGUE-V4, MATCH-V5,
  CHAMPION-MASTERY-V4, SPECTATOR-V5) and Data Dragon for static champion and item data

---

## About

dfgg is built by a four-person team as part of [Woowacourse](https://woowacourse.github.io/),
a non-profit software engineering education program run by Woowa Brothers in South Korea.
The service is free to use.

dfgg presents only information a player can already see in champion select and on the
in-game scoreboard. It does not surface any game-session-specific information that would
otherwise be unknown to the player.

dfgg is not endorsed by Riot Games and does not reflect the views or opinions of Riot Games
or anyone officially involved in producing or managing Riot Games properties. Riot Games and
all associated properties are trademarks or registered trademarks of Riot Games, Inc.
