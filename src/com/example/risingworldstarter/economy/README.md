# Economy

This package contains CivicCore's persistent, character-scoped economy.

## Components

- `EconomyApi` is the public balance contract for CivicCore and other plugins.
- `FileEconomyService` implements the API with atomic properties-file storage.
- `EconomySettings` loads starting cash, claim cost, and salary configuration.

Amounts use integer minor units (cents). Balances are stored in
`Worlds/<world>/CivicCore/balances.properties`. New accounts default to
`$25,000.00`; a chunk costs `$10,000.00`; and the default eight-hour salary is
`$1,000.00`.

Configure these values in the world-scoped `economy.properties`:

```properties
default-balance=25000.00
claim-cost=10000.00
base-salary=1000.00
```

Payday runs at 00:00, 08:00, and 16:00 in world time. It detects entry into a
new pay period and rechecks after skipped nights.

## Using the API from another plugin

```java
CivicCore civicCore = (CivicCore) getPluginByName("CivicCore");
EconomyApi economy = civicCore.getEconomyApi();
String characterKey = civicCore.getActiveCharacterKey(player);

economy.deposit(characterKey, 500);       // adds $5.00
boolean paid = economy.withdraw(characterKey, 250);
civicCore.updateBalanceLabel(player);     // refresh the player's HUD
```

The API supports account creation, lookup, balance replacement, deposits,
withdrawals, and permanent account deletion. Negative balances are rejected,
and mutations are persisted immediately.
