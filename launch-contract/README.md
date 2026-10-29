# :launch-contract

The names droidtop and Enginehost agree on when one starts the other, held once. Plain JVM; strings and
a shape check, no behaviour, no UI, no Android class.

- `EnginehostIntents`: the actions (`LAUNCH`, `CONFIGURE`, `CONFIGURE_SETTINGS`, `CONFIGURE_SAVES`, `SCAN`,
  `END_GAME`, `GAME_ENDED`), their extras (`path`, `config`, `autoinstallPlugin`, `accent`, `ended`, `error`,
  `saveFolder`, `reason`, `saveRootRefused`), the reasons a game ended, and the read-only capabilities
  provider's authority and path.
- `EnginehostConfig`: the key names of `enginehost.json` (and of the inline `config` extra), and
  `problems(text, requireVersion)`, which says in one line each what is wrong with a config: not JSON, `engine`
  missing, a key of the wrong type, a version that is not dotted numeric. Both apps' tests use it on what they
  write and read.

**Every string is what released builds already send and read.** The tests write the literals out, so a rename
cannot pass by comparing a constant with itself. Changing one needs both apps to accept both spellings for a
release first.

Not here: the engine, family and context vocabulary (`renpy`, `rpgmaker`/`vxace`, ...). It is plugin-defined, so
a closed list here would be wrong; the shared conformance vectors of Droidtop/tracker#205 hold what droidtop and
Enginehost agree on for detection.
