## Game Design

- Currently this is a prototype, focused on a realtime combat simulation.
- The combat simulation is rendered in 2d from a top-down perspective.
- Combat is between flying warships (referred to herein as simply "ships") controlled by the player,
  which start off in a player-determined quantity, loadout, and formation,
  and ships controlled by the enemy AI, which start off in a randomly-chose pre-determined quantity,
  loadout, and formation.
- For JVM and web build targets the orientation of the battle space will be landscape, with the
  player's fleet on the left and the enemy's to the right.
- On mobile the battle space will rotate to match the phone, allowing for portrait mode with the
  player fleet deployed at the bottom and the enemy at the top.
- The UI elements are to be designed so they can handle portrait orientation.
- Ships engage largely autonomously, with formations between groups of ships and ship-specific
  combat behaviour governing movement intentions and firing choices.
- Player interactions are constrained to simple commands given to formations of ships, along the
  lines of:
    - advance
    - stay at range
    - disengage
    - perform special ability (eg ramming, boarding, use special weapon)

## Ships

Details on the mechanics of ships can be found in the `docs/ship.md` file.

