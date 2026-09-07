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
import ItemsTable from '../ItemsTable.vue'
import {InventoryTypes, type InventoryDetail, type InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'
import type {StationMember} from '@/api/types'
import type {LentOutItem} from '@/api/lending'
import type {InventoryItemActionEmits} from '../itemEmits'

/**
 * The stock of a drawer, under the kinds it has been sorted into.
 *
 * <p>A heading appears only for a kind that has pieces, and the pieces nobody has named a kind for
 * come last under a plain heading of their own. That is not a gap waiting to be filled: most pieces
 * are written down by a path with nobody present to say what they are, and a station that never
 * sorts a drawer sees exactly the flat list it saw before.
 */
const props = withDefaults(
    defineProps<{
      detail: InventoryDetail
      items: InventoryItem[]
      arts: InventoryArt[]
      memberMap: Map<number, StationMember>
      lentOutItems: LentOutItem[]
      lentItemStationMap: Map<number, string>
      containerPathById: Map<number, string>
      showActions?: boolean
    }>(),
    {showActions: false},
)

defineEmits<InventoryItemActionEmits>()

const {t} = useI18n()

/** One block per kind that actually holds something, in the order the inventory shows its kinds. */
const groups = computed(() =>
    props.arts
        .map(art => ({art, items: props.items.filter(item => item.artId === art.id)}))
        .filter(group => group.items.length > 0),
)

const loose = computed(() => props.items.filter(item => item.artId == null))
</script>

<template>
  <div class="space-y-6" data-testid="items-by-art">
    <div v-for="group in groups" :key="group.art.id" class="space-y-2">
      <div class="flex items-baseline gap-2">
        <SubHeader>{{ group.art.name }}</SubHeader>
        <MutedText size="sm">{{ t('inventory.art.groupCount', {count: group.items.length}) }}</MutedText>
      </div>
      <ItemsTable
          :items="group.items"
          :has-sizes="detail.hasSizes"
          :sizes="detail.sizes"
          :members="memberMap"
          :show-actions="showActions"
          :show-history="true"
          :inventory-type="detail.inventoryType ?? InventoryTypes.INTERNAL"
          :lent-out-items="lentOutItems"
          :lent-item-map="lentItemStationMap"
          :container-path-by-id="containerPathById"
          @assign="$emit('assign', $event)"
          @unassign="$emit('unassign', $event)"
          @edit="$emit('edit', $event)"
          @mark-lost="$emit('markLost', $event)"
          @mark-found="$emit('markFound', $event)"
          @history="$emit('history', $event)"
          @delete="$emit('delete', $event)"
      />
    </div>

    <div v-if="loose.length > 0" class="space-y-2">
      <div class="flex items-baseline gap-2">
        <SubHeader>{{ t('inventory.art.looseTitle') }}</SubHeader>
        <MutedText size="sm">{{ t('inventory.art.groupCount', {count: loose.length}) }}</MutedText>
      </div>
      <ItemsTable
          :items="loose"
          :has-sizes="detail.hasSizes"
          :sizes="detail.sizes"
          :members="memberMap"
          :show-actions="showActions"
          :show-history="true"
          :inventory-type="detail.inventoryType ?? InventoryTypes.INTERNAL"
          :lent-out-items="lentOutItems"
          :lent-item-map="lentItemStationMap"
          :container-path-by-id="containerPathById"
          @assign="$emit('assign', $event)"
          @unassign="$emit('unassign', $event)"
          @edit="$emit('edit', $event)"
          @mark-lost="$emit('markLost', $event)"
          @mark-found="$emit('markFound', $event)"
          @history="$emit('history', $event)"
          @delete="$emit('delete', $event)"
      />
    </div>
  </div>
</template>
