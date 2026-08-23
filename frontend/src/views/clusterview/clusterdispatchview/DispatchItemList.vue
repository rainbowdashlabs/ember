/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type {SendableItem} from '@/api/clusterInventory'

/**
 * What is in the store, grouped by the inventory each piece comes out of, with a box beside each.
 *
 * <p>Kitting a group out means picking twenty of the same thing, so the group carries a way to take all of
 * it at once. Everything offered here is resting in the store: anything already out at a station or on its
 * way somewhere is not on the list at all rather than on it and refused.
 */
const picked = defineModel<number[]>({required: true})

const props = defineProps<{
  items: SendableItem[]
}>()

const {t} = useI18n()

interface Group {
  inventoryId: number
  inventoryName: string
  items: SendableItem[]
}

const groups = computed<Group[]>(() => {
  const byInventory = new Map<number, Group>()
  for (const item of props.items) {
    const group = byInventory.get(item.inventoryId)
        ?? {inventoryId: item.inventoryId, inventoryName: item.inventoryName, items: []}
    group.items.push(item)
    byInventory.set(item.inventoryId, group)
  }
  return [...byInventory.values()]
})

function toggle(id: number) {
  picked.value = picked.value.includes(id)
      ? picked.value.filter(other => other !== id)
      : [...picked.value, id]
}

function takeAll(group: Group) {
  const ids = group.items.map(item => item.id)
  const allPicked = ids.every(id => picked.value.includes(id))
  picked.value = allPicked
      ? picked.value.filter(id => !ids.includes(id))
      : [...new Set([...picked.value, ...ids])]
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="dispatch-items">
    <SectionHeader>{{ t('clusterInventory.dispatch.itemsTitle') }}</SectionHeader>

    <div v-for="group in groups" :key="group.inventoryId" class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="font-medium">{{ group.inventoryName }}</span>
        <SecondaryButton compact @click="takeAll(group)">
          {{ t('clusterInventory.dispatch.takeAll') }}
        </SecondaryButton>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        <label v-for="item in group.items" :key="item.id"
               class="flex items-center gap-2 rounded border border-(--border) px-2 py-1 text-sm cursor-pointer"
               :data-testid="`dispatch-item-${item.id}`">
          <CheckboxInput :model-value="picked.includes(item.id)" @update:model-value="toggle(item.id)"/>
          <span>
            {{ item.name }}
            <span v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</span>
          </span>
        </label>
      </div>
    </div>
  </NeutralContainer>
</template>
