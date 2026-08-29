# Economy

This package contains CivicCore's persistent, character-scoped economy.

## Components

- `EconomyApi` is the public balance contract for CivicCore and other plugins.
- `DatabaseEconomyService` implements the API through the shared database contract.
- `EconomySettings` loads starting cash, claim cost, and salary configuration.

Amounts use integer minor units (cents). Balances are stored in the world-scoped
database. New accounts default to
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

The active character's cash also appears as a CivicCore-owned slot on the
native inventory screen. Its bundled icon displays the formatted database
balance as the quantity. This is a virtual inventory element, so it cannot be
crafted, stored, dropped, or purchased from either marketplace.

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
