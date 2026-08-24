package com.example.risingworldstarter;

/**
 * Public contract other plugins can use to interact with player balances.
 * All amounts are expressed in the smallest currency unit (for example cents).
 */
public interface EconomyApi {
    long getBalance(String playerUid);

    long setBalance(String playerUid, long amount);

    long deposit(String playerUid, long amount);

    boolean withdraw(String playerUid, long amount);
}
