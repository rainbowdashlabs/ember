/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {ItemChipSource} from '@/components/inventory/ItemChip.vue'
import {containerPathFor} from '@/util/containerPath'
import {glyphFor} from '@/util/glyph'
import {inventory, inventoryArts, inventoryContainers, movements, stationMembers} from '@/api'
import {MovementState} from '@/api/generated/schema'
import type {
    Inventory,
    InventoryArt,
    InventoryContainer,
    InventoryItem,
    InventorySize,
    MemberWithName,
} from '@/api/generated/schema'
import type {ItemSearchText} from './itemRanking'

type ItemState = 'member' | 'storage' | 'lost' | 'free'

/** The badge a row of the picker carries for where its piece is. */
export interface ItemStateBadge {
    text: string
    variant: 'success' | 'info' | 'error' | 'neutral' | 'warning'
}

function itemState(item: InventoryItem): ItemState {
    if (item.lostAt) return 'lost'
    if (item.assignedTo != null) return 'member'
    if (item.containerId != null) return 'storage'
    return 'free'
}

/**
 * The pieces open movements have already named, which are free on the shelf and promised all the same.
 *
 * <p>A failure here leaves the set empty rather than the picker: the engine refuses a promised piece
 * anyway, so the worst of it is a refusal after the press instead of before.
 */
async function promisedPieces(): Promise<Set<number>> {
    try {
        const open = await movements.listMovements()
        return new Set(open
            .filter(movement => movement.state === MovementState.OPEN && movement.incomingItemId != null)
            .map(movement => movement.incomingItemId as number))
    } catch {
        return new Set()
    }
}

/**
 * Everything the item picker knows about the station's gear, and how it words a piece.
 *
 * <p>It loads every piece together with the shelves, members, sizes and kinds that describe it, so a
 * search runs in the browser without a request per keystroke. Kinds are only read for inventories of
 * different things, because only a kind can overrule its inventory's picture. The promised pieces
 * are asked for only where the caller wants them left out: every other picker would be paying for a
 * list it does not read.
 *
 * @param wantedSizeId the size asked for, whose pieces are drawn as a fit
 * @param withSpokenFor whether the pieces open movements have promised are to be read as well
 */
export function useItemCatalog(wantedSizeId: () => number | null | undefined, withSpokenFor: () => boolean) {
    const {t} = useI18n()

    const items = ref<InventoryItem[]>([])
    const arts = ref<InventoryArt[]>([])
    const containers = ref<InventoryContainer[]>([])
    const members = ref<MemberWithName[]>([])
    const inventories = ref<Inventory[]>([])
    const sizes = ref<InventorySize[]>([])
    const ready = ref(false)
    const spokenFor = ref<Set<number>>(new Set())

    const artById = computed(() => new Map(arts.value.map(a => [a.id, a])))
    const containerById = computed(() => new Map(containers.value.map(c => [c.id, c])))
    const memberById = computed(() => new Map(members.value.map(m => [m.id, m])))
    const inventoryById = computed(() => new Map(inventories.value.map(i => [i.id, i])))
    const sizeById = computed(() => new Map(sizes.value.map(s => [s.id, s])))
    const itemById = computed(() => new Map(items.value.map(i => [i.id, i])))

    function inventoryName(id: number): string {
        return inventoryById.value.get(id)?.name ?? t('inventory.itemPicker.unknownInventory', {id})
    }

    function memberName(id: number | null | undefined): string {
        if (id == null) return ''
        const m = memberById.value.get(id)
        return m?.name?.trim() || t('inventory.itemPicker.unknownMember', {id})
    }

    function sizeLabel(item: InventoryItem): string {
        if (!item.sizeId) return ''
        return sizeById.value.get(item.sizeId)?.label ?? ''
    }

    function baseName(item: InventoryItem): string {
        return (item.name ?? '').trim() || item.internalId || `#${item.id}`
    }

    function displayName(item: InventoryItem): string {
        const size = sizeLabel(item)
        return size ? `${baseName(item)} · ${size}` : baseName(item)
    }

    function locationLabel(item: InventoryItem): string {
        switch (itemState(item)) {
            case 'lost': return t('inventory.itemPicker.lost')
            case 'member': return t('inventory.itemPicker.heldBy', {name: memberName(item.assignedTo)})
            case 'storage': {
                const path = containerPathFor(containerById.value, item.containerId)
                return path ? t('inventory.itemPicker.storedAt', {path}) : t('inventory.itemPicker.unassigned')
            }
            default: return t('inventory.itemPicker.unassigned')
        }
    }

    /**
     * What the piece is, rather than where it is.
     *
     * <p>Where it is was what the icon used to say, which made a helmet and a jacket with the same member
     * look alike. That answer is the badge's now, and the picture says what the thing is.
     */
    function glyphOf(item: InventoryItem) {
        const art = item.artId != null ? artById.value.get(item.artId) : undefined
        const inv = inventoryById.value.get(item.inventoryId)
        return glyphFor({
            artIcon: art?.icon,
            artColor: art?.color,
            inventoryIcon: inv?.icon,
            inventoryColor: inv?.color,
            homogeneous: inv?.homogeneous,
        })
    }

    function hasWantedSize(item: InventoryItem): boolean {
        const wanted = wantedSizeId()
        return wanted != null && item.sizeId === wanted
    }

    function chipOf(item: InventoryItem): ItemChipSource {
        return {
            glyph: glyphOf(item),
            name: baseName(item),
            internalId: item.internalId,
            sizeName: sizeLabel(item),
            sizeWanted: hasWantedSize(item),
            inventoryName: inventoryName(item.inventoryId),
            location: locationLabel(item),
        }
    }

    function stateBadge(item: InventoryItem): ItemStateBadge {
        switch (itemState(item)) {
            case 'member': return {text: t('inventory.itemPicker.badgeMember'), variant: 'info'}
            case 'storage': return {text: t('inventory.itemPicker.badgeStorage'), variant: 'success'}
            case 'lost': return {text: t('inventory.itemPicker.badgeLost'), variant: 'error'}
            default: return {text: t('inventory.itemPicker.badgeFree'), variant: 'neutral'}
        }
    }

    function subtitle(item: InventoryItem): string {
        const parts: string[] = []
        if (item.internalId) parts.push(item.internalId)
        parts.push(inventoryName(item.inventoryId))
        parts.push(locationLabel(item))
        return parts.join(' · ')
    }

    const searchText = computed(() => {
        const map = new Map<number, ItemSearchText>()
        for (const item of items.value) {
            map.set(item.id, {
                id: (item.internalId ?? '').toLowerCase(),
                name: (item.name ?? '').toLowerCase(),
                size: sizeLabel(item).toLowerCase(),
                inventory: inventoryName(item.inventoryId).toLowerCase(),
                location: locationLabel(item).toLowerCase(),
            })
        }
        return map
    })

    async function load() {
        ready.value = false
        try {
            const [is, cs, ms, invs, szs] = await Promise.all([
                inventory.listAllItems(),
                inventoryContainers.listContainers(),
                stationMembers.listMembers(),
                inventory.listInventories(),
                inventory.listAllSizes(),
            ])
            items.value = is
            containers.value = cs
            members.value = ms
            inventories.value = invs
            sizes.value = szs
            const collections = invs.filter(inv => !inv.homogeneous)
            const kinds = await Promise.all(collections.map(inv => inventoryArts.listArts(inv.id)))
            arts.value = kinds.flat()
            if (withSpokenFor()) spokenFor.value = await promisedPieces()
        } catch {
            items.value = []
        } finally {
            ready.value = true
        }
    }

    return {
        items,
        ready,
        spokenFor,
        itemById,
        inventoryById,
        searchText,
        displayName,
        hasWantedSize,
        chipOf,
        stateBadge,
        subtitle,
        load,
    }
}
