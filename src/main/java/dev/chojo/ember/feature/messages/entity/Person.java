/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.wrapper.Row;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Somebody taking part in messages, by the account's register name. Messages are between accounts,
 * so the name a station membership gives somebody plays no part here.
 *
 * @param accountId the account
 * @param uid       the account's identity, which is what the page sees
 * @param firstName the register's first name, which an unnamed group is called after
 * @param lastName  the register's last name
 */
public record Person(int accountId, UUID uid, String firstName, String lastName) {

    /**
     * The person whose columns a row carries under a prefix: {@code <prefix>account_id},
     * {@code <prefix>uid}, {@code <prefix>first_name} and {@code <prefix>last_name}.
     *
     * @param row    the row
     * @param prefix what the columns start with
     * @return the person, or null where the account id is null, as it is for a deleted account
     * @throws SQLException when the row cannot be read
     */
    public static @Nullable Person read(Row row, String prefix) throws SQLException {
        Integer accountId = row.getObject(prefix + "account_id", Integer.class);
        if (accountId == null) return null;
        return new Person(
                accountId,
                row.get(prefix + "uid", UUID_STRING),
                row.getString(prefix + "first_name"),
                row.getString(prefix + "last_name"));
    }

    /**
     * The person whose columns a row carries under a prefix, where the row always names one.
     *
     * @param row    the row
     * @param prefix what the columns start with
     * @return the person
     * @throws SQLException when the row cannot be read
     */
    public static Person require(Row row, String prefix) throws SQLException {
        return new Person(
                row.getInt(prefix + "account_id"),
                row.get(prefix + "uid", UUID_STRING),
                row.getString(prefix + "first_name"),
                row.getString(prefix + "last_name"));
    }

    /**
     * The select list naming the account columns {@link #read} expects, for an account table alias.
     *
     * @param alias  the alias of the joined account table
     * @param prefix what the columns are named with
     * @return the columns, comma separated
     */
    public static String columns(String alias, String prefix) {
        return "%1$s.id AS %2$saccount_id, %1$s.uid AS %2$suid, %1$s.first_name AS %2$sfirst_name, %1$s.last_name AS %2$slast_name"
                .formatted(alias, prefix);
    }
}
