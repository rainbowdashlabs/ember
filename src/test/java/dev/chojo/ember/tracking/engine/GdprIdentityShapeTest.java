/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.IdentityType;
import dev.chojo.ember.tracking.Status;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every rule the deletion engine may run has to make a statement the database would accept.
 *
 * <p>A table that names none of its identity columns is treated permissively, so each of its rules is
 * tried for every kind of identity in turn. A member's uuid held against an integer column is not a
 * rule that simply matches nothing: the database refuses the statement outright, and erasing an
 * account stops there with the rest of it undone. The shapes are therefore checked here rather than
 * found out one deletion at a time.
 */
class GdprIdentityShapeTest {

    private static dev.chojo.ember.tracking.DataTracking tracking;

    @BeforeAll
    static void setup() throws IOException {
        tracking = DataTrackingLoader.loadFromClasspath();
    }

    private static boolean holdsUuid(String type) {
        return "uuid".equals(type);
    }

    private static boolean holdsInteger(String type) {
        return "int4".equals(type) || "int8".equals(type);
    }

    @Test
    void anIdentityIsOnlyEverMatchedAgainstAColumnShapedToHoldIt() {
        List<String> wrong = new ArrayList<>();
        for (var entry : tracking.tables().entrySet()) {
            var table = entry.getValue();
            var export = table.gdprExport();
            if (export == null || export.status() != Status.TRACKED || export.identityColumns() == null) continue;
            for (var identity : export.identityColumns()) {
                var column = table.columns() == null
                        ? null
                        : table.columns().stream()
                                .filter(c -> c.name().equals(identity.column()))
                                .findFirst()
                                .orElse(null);
                if (column == null || column.type() == null) continue;
                boolean fits = identity.type() == IdentityType.MEMBER_UID
                        ? holdsUuid(column.type())
                        : holdsInteger(column.type());
                if (!fits) {
                    wrong.add(entry.getKey() + "." + column.name() + " is " + column.type() + " but carries "
                            + identity.type());
                }
            }
        }
        if (!wrong.isEmpty()) {
            fail("These identity columns cannot hold the identity they declare:\n  " + String.join("\n  ", wrong));
        }
    }
}
