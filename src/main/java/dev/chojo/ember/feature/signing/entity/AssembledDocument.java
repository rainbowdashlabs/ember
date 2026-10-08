/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * A signed document put together for one signing state, ready to be sealed: the frozen content, the record
 * page after it and the evidence attached.
 *
 * <p>The array is handed over as it is, without a copy.
 *
 * @param pdf        the document, not yet sealed
 * @param recordPage the number of the record's first page, counted from 1, which a signer's mark refers to
 */
public record AssembledDocument(byte[] pdf, int recordPage) {}
