# VaultSync Plan

1. Establish Java 21/Maven build, plugin metadata, MIT license, documentation, and CI.
2. Add pooled MySQL/MariaDB connectivity and automatic schema setup with snapshot-version compatibility.
3. Persist player inventories, armor, offhand, ender chest, experience, survival state, flight and movement settings, health scaling, attribute base values, effects, statistics by type, exact advancement criteria, recipes, bed spawn, and compass target.
4. Capture and apply Bukkit state on the server thread and serialize database work through an ordered worker.
5. Add autosave, join restoration, quit saving, bounded shutdown flushing, status/save/restore/info/reload commands, and offline restore scheduling.
6. Keep existing version-1 snapshots readable while writing version-2 snapshots.
7. Run CI compilation/package checks and verify the generated artifact.

Safety rules: failed serialization must not replace a previous snapshot; never access mutable Bukkit player state from database workers; report failed database writes; back up the world and database before restore; document that arbitrary plugin-owned data and world chunks are not included.

Validation: Maven CI verifies compilation, tests, and packaging. Runtime smoke tests on each target Paper version and a real MySQL/MariaDB instance remain required before production use.
