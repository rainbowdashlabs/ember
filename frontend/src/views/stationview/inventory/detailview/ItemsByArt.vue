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
import type {InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'
import type {MemberIdentity} from '@/api/types'
import InventoryItemTable from '../itemtable/InventoryItemTable.vue'
import type {ItemTableApi} from '../itemtable/useItemTable'
import type {InventoryItemActionEmits} from '../itemEmits'

/**
 * The stock of a drawer, under the kinds it has been sorted into.
 *
 * <p>A heading appears only for a kind that has pieces, and the pieces nobody has named a kind for
 * come last under a plain heading of their own. That is not a gap waiting to be filled: most pieces
 * are written down by a path with nobody present to say what they are, and a station that never
 * sorts a drawer sees exactly the flat list it saw before.
 *
 * <p>Every block is a part of one table, so the columns, the sort and the filters chosen in any of
 * them hold for all of them.
 */
const props = withDefaults(
    defineProps<{
      items: ItemTableApi
      arts: InventoryArt[]
      memberIdentity: (memberId: number) => MemberIdentity | null | undefined
      lentItemStationMap: Map<number, string>
      containerPathById: Map<number, string>
      showActions?: boolean
    }>(),
    {showActions: false},
)

const emit = defineEmits<InventoryItemActionEmits>()

const {t} = useI18n()
const {isMobile} = useBreakpoint()

interface Block {
  key: string | number
  title: string
  items: InventoryItem[]
}

/** One block per kind that actually holds something, in the order the inventory shows its kinds. */
const blocks = computed<Block[]>(() => {
  const shown = props.items.table.rows
  const kinds = props.arts.map(art => ({
    key: art.id,
    title: art.name,
    items: shown.filter(item => item.artId === art.id),
  }))
  const loose = {key: 'loose', title: t('inventory.art.looseTitle'), items: shown.filter(item => item.artId == null)}
  return [...kinds, loose].filter(block => block.items.length > 0)
})
</script>

<template>
  <div class="space-y-6" data-testid="items-by-art">
    <RecordCardControls v-if="isMobile" :table="items.table"/>
    <div v-for="block in blocks" :key="block.key" class="space-y-2">
      <div class="flex items-baseline gap-2">
        <SubHeader>{{ block.title }}</SubHeader>
        <MutedText size="sm">{{ t('inventory.art.groupCount', {count: block.items.length}) }}</MutedText>
      </div>
      <InventoryItemTable
          :container-path-by-id="containerPathById"
          :items="items"
          :lent-item-map="lentItemStationMap"
          :member-identity="memberIdentity"
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
