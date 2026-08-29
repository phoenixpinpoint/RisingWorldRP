package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.groups.GroupService;
import com.example.risingworldstarter.journal.JournalService;
import com.example.risingworldstarter.userstore.UserStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
final class ValidationMockTest {
    @Mock Database database;

    @Test void invalidInputIsRejectedBeforePersistence() {
        var groups = new GroupService(database);
        var journals = new JournalService(database);
        var userStore = new UserStoreService(database);
        assertThrows(IllegalArgumentException.class, () -> groups.deposit("group", "actor", 0));
        assertThrows(IllegalArgumentException.class, () -> journals.open(" "));
        assertThrows(IllegalArgumentException.class,
                () -> userStore.create("seller", "Seller", (short) 1, 0, 0, 100));
        verifyNoInteractions(database);
    }
}
