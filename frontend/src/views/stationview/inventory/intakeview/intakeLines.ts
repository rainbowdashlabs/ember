/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {IntakeRow, ItemOwnerName} from '@/api/inventory'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import type {StationMember} from '@/api/types'
import {buildItemMetadata} from '@/views/stationview/inventory/detailview/itemMetadata'
import {
    getMemberFirstName,
    getMemberLastName,
    memberDisplayName,
} from '@/views/stationview/members/listview/useMemberData'

/** One line of the stock-taking table, as the editor holds it. */
export interface IntakeLine {
    memberId: number | null
    memberName: string
    /** The two halves of the name, kept apart so the table can be put in either order. */
    firstName: string
    lastName: string
    /** The size as the select holds it, which is a string or nothing chosen. */
    sizeId: string
    internalId: string
    fields: Record<string, unknown>
    /**
     * Whether this line was asked for outright. Gear nobody ever wrote a number on, in an inventory
     * that keeps no sizes and no fields, leaves nothing to fill in, so the line says so itself.
     */
    askedFor: boolean
}

/** A line for somebody, with nothing written on it yet. */
export function lineFor(member: StationMember): IntakeLine {
    return {
        memberId: member.id,
        memberName: memberDisplayName(member),
        firstName: getMemberFirstName(member),
        lastName: getMemberLastName(member),
        sizeId: '',
        internalId: '',
        fields: {},
        askedFor: false,
    }
}

/** Whether this line describes a piece, which is what decides whether it is sent at all. */
export function namesAPiece(line: IntakeLine): boolean {
    return line.askedFor
        || line.sizeId !== ''
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
        askedFor: line.askedFor,
    }))
}
