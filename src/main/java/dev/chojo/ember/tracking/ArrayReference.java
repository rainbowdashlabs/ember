/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

/**
 * An array column whose elements are ids of rows in another table. The database cannot hold a foreign
 * key on the elements of an array, so the schema does not say this and the tracking file has to.
 *
 * <p>A station transfer gives the rows of {@code refTable} new ids, and the import moves every element
 * to the id its row got on the destination. An element whose row did not arrive is left out.
 *
 * <p>Example: on {@code attendance_report_preset} the reference {@code column=group_ids,
 * refTable=member_group} keeps a saved report pointing at the groups it was saved with.
 *
 * @param column   the array column on this table
 * @param refTable the table whose integer {@code id} each element names
 */
public record ArrayReference(String column, String refTable) {}
