/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    CheckResult,
    InventoryContainer,
    InventoryContainerKind,
    InventoryItem,
    InventorySize,
    RequiredInventoryItem,
} from '@/api/generated/schema'
import type {CheckEntry} from '@/composables/useMemberCheck'
import type {PitchInventoryCheck, PitchRapidCheck, PitchStats, PitchStorage} from './pitchTypes'

/**
 * The inventory a demonstration shows. It is handed to the application's own check, statistics and
 * storage components, so the labels, colours and empty slots are the ones the screens really draw.
 */
const SIZES: InventorySize[] = [
    {id: 1, inventoryId: 1, label: '152', position: 0, note: ''},
    {id: 2, inventoryId: 1, label: '164', position: 1, note: ''},
    {id: 3, inventoryId: 1, label: '38', position: 2, note: ''},
]

const CLOTHING: RequiredInventoryItem = {
    inventoryId: 1, inventoryName: 'Einsatzkleidung', inventoryType: 'INTERNAL',
    hasSizes: true, homogeneous: true, sizes: SIZES, requiredQuantity: 4, assignedQuantity: 3, inExchangeQuantity: 0,
}

function item(id: number, name: string, internalId: string, sizeId: number, assignedTo: number | null): InventoryItem {
    return {
        id, inventoryId: 1, internalId, name, sizeId, artId: null, metadata: {fields: {}}, assignedTo,
        lostAt: null, lostNote: null, lostNoteBy: null, ownerKind: 'STATION', ownerClusterId: null,
        ownerStationId: null, loanRequestItemId: null, custody: assignedTo == null ? 'WITH_OWNER' : 'WITH_MEMBER',
        custodyStationId: null, custodyPartnerStationId: null, custodyMovementId: null, containerId: null,
    }
}

const JACKET = item(142, 'Einsatzjacke', 'EK-0142', 1, 1)
const TROUSERS = item(311, 'Einsatzhose', 'EK-0311', 1, 1)
const BOOTS = item(755, 'Stiefel', 'EK-0755', 3, 1)

const FREE: InventoryItem[] = [item(143, 'Einsatzjacke', 'EK-0143', 2, null)]

function sizeLabel(req: RequiredInventoryItem, sizeId?: number | null): string {
    return req.sizes.find(size => size.id === sizeId)?.label ?? ''
}

function itemLabel(entry: InventoryItem, req: RequiredInventoryItem): string {
    const size = sizeLabel(req, entry.sizeId)
    return [entry.name, size, entry.internalId].filter(Boolean).join(' · ')
}

/** What the check and the quick run both have written down about the lost jacket. */
const LOST_JACKET_NOTE: ReadonlyMap<number, string> = new Map([[755, 'Beim Zeltlager verloren']])

/** The check of one member: one item confirmed, one lost, and a slot that stayed empty. */
export const INVENTORY_CHECK: PitchInventoryCheck = {
    req: CLOTHING,
    assignedItems: [JACKET, BOOTS],
    availableItems: [],
    emptySlotCount: 1,
    itemResults: new Map([[142, 'CONFIRMED'], [755, 'LOST']]) as ReadonlyMap<number, CheckResult>,
    itemNotes: LOST_JACKET_NOTE,
    slotsNotInPossession: new Set() as ReadonlySet<string>,
    slotProcurements: new Set() as ReadonlySet<string>,
    slotSelections: new Map() as ReadonlyMap<string, string>,
    sizeLabel,
    itemLabel,
}

/** The same check as a quick run: one item at a time, with the scanner at hand. */
export const INVENTORY_RAPID: PitchRapidCheck = {
    uncheckedEntries: [
        {type: 'item', item: TROUSERS, req: CLOTHING},
        {type: 'slot', req: CLOTHING, slotIndex: 0},
    ] as CheckEntry[],
    availableForInventory: () => FREE,
    sizeLabel,
    itemLabel,
    itemNotes: LOST_JACKET_NOTE,
    movementOf: () => null,
}

export const INVENTORY_STATS: PitchStats = {
    totalCount: 128, freeCount: 22, assignedCount: 103, lostCount: 3, lentOutCount: 0,
    hasSizes: false, sizeStats: [],
}

const KINDS: InventoryContainerKind[] = [
    {id: 1, stationId: 'wache', key: 'room', label: 'Raum', icon: 'warehouse', color: null, sortOrder: 0, enabled: true},
    {id: 2, stationId: 'wache', key: 'cupboard', label: 'Schrank', icon: 'box-archive', color: null, sortOrder: 1, enabled: true},
    {id: 3, stationId: 'wache', key: 'drawer', label: 'Schublade', icon: 'inbox', color: null, sortOrder: 2, enabled: true},
    {id: 4, stationId: 'wache', key: 'box', label: 'Kiste', icon: 'box', color: null, sortOrder: 3, enabled: true},
    {id: 5, stationId: 'wache', key: 'shelf', label: 'Regal', icon: 'layer-group', color: null, sortOrder: 4, enabled: true},
    {id: 6, stationId: 'wache', key: 'vehicle', label: 'Fahrzeug', icon: 'suitcase', color: null, sortOrder: 5, enabled: true},
]

function container(id: number, name: string, kindId: number,
                   parentId: number | null, internalId: string | null = null): InventoryContainer {
    return {
        id, stationId: 'wache', parentId, internalId, name, kindId, description: '', createdAt: '', createdBy: null,
    }
}

const CONTAINERS = [
    container(1, 'Gerätehaus', 1, null),
    container(2, 'Schrank 2', 2, 1, 'B-014'),
    container(3, 'Schublade oben', 3, 2, 'B-015'),
    container(4, 'Kiste Schläuche', 4, 2, 'B-021'),
    container(5, 'Regal Atemschutz', 5, 1, 'B-030'),
    container(6, 'Fahrzeug 1', 6, null, 'B-100'),
]

/**
 * The storage as the application's own container tree takes it, with the lookups it reads built
 * from the containers and their kinds.
 */
function storageOf(containers: InventoryContainer[], kinds: InventoryContainerKind[]): PitchStorage {
    const childrenByParent = new Map<number, InventoryContainer[]>()
    for (const entry of containers) {
        if (entry.parentId == null) continue
        childrenByParent.set(entry.parentId, [...(childrenByParent.get(entry.parentId) ?? []), entry])
    }
    return {
        roots: containers.filter(entry => entry.parentId == null),
        childrenByParent,
        kindById: new Map(kinds.map(kind => [kind.id, kind])),
    }
}

export const INVENTORY_STORAGE = storageOf(CONTAINERS, KINDS)
