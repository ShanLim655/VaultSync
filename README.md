# VaultSync

VaultSync is an open-source Paper plugin that stores vanilla player snapshots in MySQL or MariaDB. It uses Java 21, Maven, HikariCP, and the MIT License.

## Requirements

- Java 21+
- Paper 1.21.x
- MySQL 8+ or MariaDB 10.6+
- A dedicated database and user with permission to create and modify VaultSync tables

## Build

Run `mvn --batch-mode clean verify`. The shaded plugin is generated at `target/VaultSync.jar`.

## Installation

1. Back up your server world and database.
2. Put `VaultSync.jar` in `plugins/` and start the server once.
3. Stop the server and configure `plugins/VaultSync/config.yml`.
4. Use a dedicated database account and enable TLS for remote database connections.
5. Start the server and check the console for the successful database connection message.
6. Test on a staging server before using it on a production network.

## Configuration

Configure the database endpoint, credentials, TLS, connection pool, autosave interval, join restoration, quit saving, shutdown saving, and shutdown timeout in `config.yml`. Database reads and writes are serialized through a dedicated worker. Bukkit state is captured and applied on the server thread.

Never expose database credentials or enable public-key retrieval unless your database provider explicitly requires it. For a remote database, enable TLS and use a trusted certificate configuration.

## Commands

- `/vaultsync status` — test the database connection.
- `/vaultsync save <player>` — save an online player's current state.
- `/vaultsync restore <player>` — restore a stored snapshot to an online player or queue it for an offline player to apply on their next join.
- `/vaultsync info <player>` — inspect an online or offline player's stored snapshot.
- `/vaultsync reload` — reload configuration values that do not require a database reconnect.

Permissions: `vaultsync.admin`, `vaultsync.status`, `vaultsync.save`, `vaultsync.restore`, `vaultsync.info`, and `vaultsync.reload`. Operators receive these by default.

## Persisted state

Snapshots include inventory contents, armor, offhand, ender chest, XP and level, health and food state, fire and air state, game mode, flight and movement speeds, absorption, health scaling, bed spawn and compass target, attribute base values, full potion-effect flags, untyped and typed statistics, exact awarded advancement criteria, and discovered recipes.

VaultSync does not copy world chunks, locations, arbitrary plugin-owned data, permissions, economy balances, or other plugins' private state. Back up the world and database before restoring snapshots. Existing version-1 snapshots remain readable and are upgraded to the version-2 snapshot format the next time the player is saved.

## Data safety and compatibility

Failed serialization never replaces the existing database snapshot. Save requests are queued in order and command saves report their actual database result. Offline restore queues an explicit marker and applies the saved database snapshot on the player's next join. Runtime smoke tests on each target Paper version are still required before production use; a passing compile is not a substitute for server testing.
