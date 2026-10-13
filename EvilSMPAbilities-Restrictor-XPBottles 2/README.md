# EvilSMPAbilities

Items:
- Restrictor: enchanted comparator; on hit blocks Elytra gliding, vanilla spear melee/use, and throwing Experience Bottles for 5 minutes.
- Tickle Feather: hitting a player applies Slow Falling for 3 minutes; the wielder has a 5-minute cooldown.
- Ability Suppressor: removes listed beneficial potion effects and temporarily sets target max health to 10 hearts for 5 minutes, then restores the saved max-health base value.
- Armour Breaker: reduces the target's armour attribute by 8 for 10 seconds.

Commands (operators by default):
- `/evilsmpitems give <player> tickle`
- `/evilsmpitems give <player> suppressor`
- `/evilsmpitems give <player> armourbreaker`
- `/evilsmpitems give <player> restrictor`

Build: upload this project to GitHub preserving `.github/workflows/build.yml`, open Actions, run the workflow, download the `EvilSMPAbilities-jar` artifact, extract it, and put `EvilSMPAbilities.jar` in the server's `plugins` folder.

Compatibility note: this source targets Paper 1.21.4 API and Java 21. It has not been verified against your exact server build; test on a backup/test server first. This ZIP contains source code, not a precompiled JAR.

Note: restrictions cover vanilla spear interactions/melee and XP bottle throwing; compatibility with thrown spear mechanics or other plugins must be tested on the exact server version.

Normal XP orb collection is unaffected by the Restrictor.
