# :version

Owns version parsing and ordering. Holds no keys and no network code.

- `Version`, `VersionSeries`, `VersionConstraint`: dotted integers with an optional build counter
  (`1.0.0-3`), the plugin-bundle convention, an allow-list of exact versions and ranges. Lifted
  from Enginehost.
- `Versions.compareLoose`: order for free-form version strings (folder names, store versions,
  image tags). Lifted from droidtop.

The two orderings differ on purpose: use `Version` where a string must be a version, and
`compareLoose` where it may be anything.
