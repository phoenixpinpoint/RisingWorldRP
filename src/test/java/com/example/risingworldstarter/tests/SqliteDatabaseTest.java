package com.example.risingworldstarter.tests;

import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

final class SqliteDatabaseTest extends SqliteTestSupport {
    @Test void transactionRollsBackSqlAndRuntimeFailures() {
        assertThrows(IllegalStateException.class,()->database.transaction(connection->{
            try(PreparedStatement insert=connection.prepareStatement("INSERT INTO balances(account_id,balance) VALUES('one',10)")){insert.executeUpdate();}
            throw new IllegalStateException("rollback");
        }));
        long balanceCount=database.read(connection->{try(var query=connection.prepareStatement("SELECT COUNT(*) FROM balances");var row=query.executeQuery()){return row.getLong(1);}});
        assertEquals(0L,balanceCount);
        assertThrows(IllegalStateException.class,()->database.read(connection->{throw new SQLException("failure");}));
    }

    @Test void closeIsSafeAndSchemaIsIdempotent() {
        database.close();
        assertDoesNotThrow(database::close);
        long metadataCount=database.read(connection->{try(var query=connection.prepareStatement("SELECT COUNT(*) FROM metadata");var row=query.executeQuery()){return row.getLong(1);}});
        assertEquals(0L,metadataCount);
    }
}
