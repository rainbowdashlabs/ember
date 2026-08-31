/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InventoryItem, InventorySize, RequiredInventoryItem} from '@/api/inventory'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'
import type {CheckEntry} from '@/composables/useMemberCheck'
import type {CheckResult} from '@/api/inventoryCheck'
import type {PitchInventoryCheck, PitchRapidCheck, PitchStats, PitchStorage} from './pitchTypes'

/**
 * The inventory a demonstration shows. It is handed to the application's own check, statistics and
 * storage components, so the labels, colours and empty slots are the ones the screens really draw.
 */
const SIZES: InventorySize[] = [
    {id: 1, inventoryId: 1, label: '152', position: 0},
    {id: 2, inventoryId: 1, label: '164', position: 1},
    {id: 3, inventoryId: 1, label: '38', position: 2},
]

const CLOTHING: RequiredInventoryItem = {
    inventoryId: 1, inventoryName: 'Einsatzkleidung', inventoryType: 'INTERNAL',
    hasSizes: true, homogeneous: true, sizes: SIZES, requiredQuantity: 4, assignedQuantity: 3, inExchangeQuantity: 0,
}

function item(id: number, name: string, internalId: string, sizeId: number): InventoryItem {
    return {id, inventoryId: 1, name, internalId, sizeId, assignedTo: 1}
}

const JACKET = item(142, 'Einsatzjacke', 'EK-0142', 1)
const TROUSERS = item(311, 'Einsatzhose', 'EK-0311', 1)
const BOOTS = item(755, 'Stiefel', 'EK-0755', 3)

const FREE: InventoryItem[] = [
    {id: 143, inventoryId: 1, name: 'Einsatzjacke', internalId: 'EK-0143', sizeId: 2},
]

function sizeLabel(req: RequiredInventoryItem, sizeId?: number | null): string {
    return req.sizes.find(size => size.id === sizeId)?.label ?? ''
}

function itemLabel(entry: InventoryItem, req: RequiredInventoryItem): string {
    const size = sizeLabel(req, entry.sizeId)
    return [entry.name, size, entry.internalId].filter(Boolean).join(' · ')
}

/** The check of one member: one item confirmed, one lost, and a slot that stayed empty. */
export const INVENTORY_CHECK: PitchInventoryCheck = {
    req: CLOTHING,
    assignedItems: [JACKET, BOOTS],
    availableItems: [],
    emptySlotCount: 1,
    itemResults: new Map<number, CheckResult>([[142, 'CONFIRMED'], [755, 'LOST']]),
    itemNotes: new Map([[755, 'Beim Zeltlager verloren']]),
    slotsNotInPossession: new Set<string>(),
    slotProcurements: new Set<string>(),
    slotSelections: new Map<string, string>(),
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
    itemNotes: new Map([[755, 'Beim Zeltlager verloren']]),
}

export const INVENTORY_STATS: PitchStats = {
    totalCount: 128, freeCount: 22, assignedCount: 103, lostCount: 3, lentOutCount: 0,
    hasSizes: false, sizeStats: [],
}

const KINDS: InventoryContainerKind[] = [
    {id: 1, stationId: 'wache', key: 'room', label: 'Raum', icon: 'warehouse', sortOrder: 0, enabled: true},
    {id: 2, stationId: 'wache', key: 'cupboard', label: 'Schrank', icon: 'box-archive', sortOrder: 1, enabled: true},
    {id: 3, stationId: 'wache', key: 'drawer', label: 'Schublade', icon: 'inbox', sortOrder: 2, enabled: true},
    {id: 4, stationId: 'wache', key: 'box', label: 'Kiste', icon: 'box', sortOrder: 3, enabled: true},
    {id: 5, stationId: 'wache', key: 'shelf', label: 'Regal', icon: 'layer-group', sortOrder: 4, enabled: true},
    {id: 6, stationId: 'wache', key: 'vehicle', label: 'Fahrzeug', icon: 'suitcase', sortOrder: 5, enabled: true},
]

function container(id: number, name: string, kindId: number,
                   parentId: number | null, internalId?: string): InventoryContainer {
    return {id, stationId: 'wache', parentId, internalId, name, kindId, description: '', createdAt: ''}
}

const CONTAINERS = [
    container(1, 'Gerätehaus', 1, null),
    container(2, 'Schrank 2', 2, 1, 'B-014'),
    container(3, 'Schublade oben', 3, 2, 'B-015'),
    container(4, 'Kiste Schläuche', 4, 2, 'B-021'),
    container(5, 'Regal Atemschutz', 5, 1, 'B-030'),
    container(6, 'Fahrzeug 1', 6, null, 'B-100'),
]

export const INVENTORY_STORAGE: PitchStorage = {
    roots: CONTAINERS.filter(entry => entry.parentId == null),
    childrenByParent: CONTAINERS.reduce((map, entry) => {
        if (entry.parentId == null) return map
        map.set(entry.parentId, [...(map.get(entry.parentId) ?? []), entry])
        return map
    }, new Map<number, InventoryContainer[]>()),
    kindById: new Map(KINDS.map(kind => [kind.id, kind])),
}
