/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RecordCardControls from '@/components/table/RecordCardControls.vue'
import TableFilterDialog from '@/components/table/TableFilterDialog.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import type {InventoryArt, InventoryItem} from '@/api/generated/schema'
import type {PersonIdentity} from '@/util/personIdentity'
import InventoryItemTable from '../itemtable/InventoryItemTable.vue'
import PieceThumb from '@/components/inventory/PieceThumb.vue'
import {artPictureUrl} from '@/api/inventoryPictures'
import {pieceGlyph, type Glyph, type GlyphOwner} from '@/util/glyph'
import type {ItemTableApi} from '../itemtable/useItemTable'
import type {ItemSelection} from '../itemtable/useItemSelection'
import type {InventoryItemActionEmits} from '../itemEmits'

/**
 * The stock of a drawer, under the kinds it has been sorted into.
 *
 * <p>A heading appears only for a kind that has pieces, and the pieces nobody has named a kind for
 * come last under a plain heading of their own. That is not a gap waiting to be filled: most pieces
 * are written down by a path with nobody present to say what they are, and a station that never
 * sorts a drawer sees exactly the flat list it saw before.
 *
 * <p>Each heading carries the kind's photo where it has one, otherwise its icon, or the inventory's
 * icon where the kind has none.
 *
 * <p>Every block is a part of one table, so the columns, the sort and the filters chosen in any of
 * them hold for all of them.
 */
const props = withDefaults(
    defineProps<{
      items: ItemTableApi
      arts: InventoryArt[]
      memberIdentity: (memberId: number) => PersonIdentity | null | undefined
      lentItemStationMap: ReadonlyMap<number, string>
      /** The movement carrying each piece an open movement carries. */
      movementItemMap?: ReadonlyMap<number, number>
      containerPathById: Map<number, string>
      showActions?: boolean
      /** The pieces ticked for a change to all of them, shared by every block. */
      selection?: ItemSelection
      /** The inventory's own picture, drawn for a kind that has none and for the loose pieces. */
      inventoryGlyph?: GlyphOwner
    }>(),
    {showActions: false, movementItemMap: () => new Map(), selection: undefined, inventoryGlyph: undefined},
)

const emit = defineEmits<InventoryItemActionEmits>()

const {t} = useI18n()
const {isMobile} = useBreakpoint()

interface Block {
  key: string | number
  title: string
  glyph: Glyph
  /** The kind's own photo, drawn in place of the picture where it has one. */
  pictureSrc: string | null
  items: InventoryItem[]
}

/** One block per kind that actually holds something, in the order the inventory shows its kinds. */
const blocks = computed<Block[]>(() => {
  const byArt = new Map<number | null, InventoryItem[]>()
  for (const item of props.items.table.rows) {
    const artId = item.artId ?? null
    const bucket = byArt.get(artId)
    if (bucket) bucket.push(item)
    else byArt.set(artId, [item])
  }
  const inventory = {...props.inventoryGlyph, homogeneous: false}
  const kinds = props.arts.map(art => ({
    key: art.id,
    title: art.name,
    glyph: pieceGlyph(art, inventory),
    pictureSrc: art.pictureKey ? artPictureUrl(art.inventoryId, art.id, art.pictureKey, 64) : null,
    items: byArt.get(art.id) ?? [],
  }))
  const loose = {
    key: 'loose',
    title: t('inventory.art.looseTitle'),
    glyph: pieceGlyph(null, inventory),
    pictureSrc: null,
    items: byArt.get(null) ?? [],
  }
  return [...kinds, loose].filter(block => block.items.length > 0)
})
</script>

<template>
  <div class="space-y-6" data-testid="items-by-art">
    <RecordCardControls v-if="isMobile" :table="items.table"/>
    <div v-for="block in blocks" :key="block.key" class="space-y-2">
      <div class="flex items-center gap-2">
        <PieceThumb :glyph="block.glyph" :picture-src="block.pictureSrc"/>
        <SubHeader>{{ block.title }}</SubHeader>
        <MutedText size="sm">{{ t('inventory.art.groupCount', {count: block.items.length}) }}</MutedText>
      </div>
      <InventoryItemTable
          :container-path-by-id="containerPathById"
          :items="items"
          :lent-item-map="lentItemStationMap"
          :movement-item-map="movementItemMap"
          :member-identity="memberIdentity"
          :selection="selection"
          :show-actions="showActions"
          :subset="block.items"
          @assign="emit('assign', $event)"
          @delete="emit('delete', $event)"
          @edit="emit('edit', $event)"
          @history="emit('history', $event)"
          @mark-found="emit('markFound', $event)"
          @mark-lost="emit('markLost', $event)"
          @unassign="emit('unassign', $event)"
      />
    </div>
    <TableFilterDialog :table="items.table"/>
  </div>
</template>
