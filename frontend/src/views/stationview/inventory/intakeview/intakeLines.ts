/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {IntakeRow, ItemOwnerName} from '@/api/inventory'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import {buildItemMetadata} from '@/views/stationview/inventory/detailview/itemMetadata'

/** One line of the stock-taking table, as the editor holds it. */
export interface IntakeLine {
    memberId: number | null
    memberName: string
    /** The size as the select holds it, which is a string or nothing chosen. */
    sizeId: string
    internalId: string
    fields: Record<string, unknown>
}

/** A line for somebody, with nothing written on it yet. */
export function lineFor(memberId: number | null, memberName: string): IntakeLine {
    return {memberId, memberName, sizeId: '', internalId: '', fields: {}}
}

/** Whether this line describes a piece, which is what decides whether it is sent at all. */
export function namesAPiece(line: IntakeLine): boolean {
    return line.sizeId !== ''
        || line.internalId.trim() !== ''
        || Object.values(line.fields).some(value => value !== undefined && value !== null && value !== '')
}

/**
 * The lines as the server takes them, with the empty ones left out.
 *
 * <p>They are left out here as well as there: what is sent is then what the reader filled in, and a
 * request full of empty rows says nothing about a table that was mostly left alone.
 */
export function rowsOf(
    lines: IntakeLine[],
    fields: InventoryFieldDefinition[],
    ownerKind?: ItemOwnerName,
): IntakeRow[] {
    return lines.filter(namesAPiece).map(line => ({
        memberId: line.memberId,
        internalId: line.internalId.trim() || null,
        sizeId: line.sizeId ? Number(line.sizeId) : null,
        ownerKind,
        metadata: buildItemMetadata(fields, line.fields),
    }))
}
