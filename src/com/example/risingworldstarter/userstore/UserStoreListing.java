package com.example.risingworldstarter.userstore;

public record UserStoreListing(long id, String sellerKey, String sellerName,
                               short itemType, int itemVariant, int quantity, long price) { }
