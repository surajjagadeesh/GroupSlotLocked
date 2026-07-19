# Group Slot Locked

A RuneLite plugin for **voluntary Group Ironman challenge rules** where each player can only equip gear slots they have "claimed" with slot tokens.

The plugin is **disabled by default**. Enable it from the RuneLite plugin panel when your team wants to use these self-imposed limits.

## How it works

Each equipment slot (head, cape, amulet, weapon, body, shield, legs, gloves, boots, ring, ammo) can be represented by a Wilderness team cape token. While playing with this plugin enabled, you can enforce rules such as:

- Hold at most **5** slot tokens in your personal bank and inventory
- Equip at most **5** tracked gear slots at once
- Only equip a slot if you currently hold that slot's token (and it is not in group storage)

These limits are enforced locally as a challenge helper. The plugin does not modify game actions sent to the server unless you choose optional click-blocking behavior in settings.

## Features

- Tracks slot token claims and validates equipped gear
- Red highlights on items that violate your current rules
- Optional full-screen penalty overlay for illegal loadouts
- Custom slot icons and names for Wilderness cape tokens
- Worn Equipment tab badges showing claimed/unclaimed slot status
- Bank search by custom slot names
- Optional chat warnings when rules are broken
- Menu relabeling for slot tokens

## Configuration

Open the plugin settings to adjust:

- Max held tokens
- Max equipped slots
- Penalty overlay, restricted-item highlights, and chat warnings
- Token icon replacement and equipment indicators
- Bank refresh interval

Custom slot icons can be placed in `.runelite/group-slot-locked/icons/`.

## Support

Report issues on [GitHub](https://github.com/surajjagadeesh/GroupSlotLocked/issues).

## License

BSD 2-Clause. See [LICENSE](LICENSE).
