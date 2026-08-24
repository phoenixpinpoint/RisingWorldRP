package com.example.risingworldstarter;

/**
 * Public contract other plugins can use to interact with player balances.
 * All amounts are expressed in the smallest currency unit (for example cents).
 */
public interface EconomyApi {
    /** Creates a new account with the supplied balance; does nothing if it already exists. */
    long createAccount(String playerUid, long initialBalance);

    long getBalance(String playerUid);

    boolean hasAccount(String playerUid);

    long setBalance(String playerUid, long amount);

    long deposit(String playerUid, long amount);

    boolean withdraw(String playerUid, long amount);
}
