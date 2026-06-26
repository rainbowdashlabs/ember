/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import type {AvailableItemDetail} from '@/api/lending'

const props = defineProps<{
  loading: boolean
  availableItems: AvailableItemDetail[]
  selectedItemIds: Set<number>
  assigning: boolean
}>()

const emit = defineEmits<{
  toggleItem: [itemId: number]
  assignAndLend: []
}>()

const {t} = useI18n()

const itemFilter = ref('')

const filteredAvailableItems = computed(() => {
  const query = itemFilter.value.trim().toLocaleUpperCase('en-US')
  if (!query) return props.availableItems
  return props.availableItems.filter(i =>
      (i.internalId ?? '').toLocaleUpperCase('en-US').includes(query)
      || (i.itemName ?? '').toLocaleUpperCase('en-US').includes(query),
  )
})

const groupedAvailableItems = computed(() => {
  const groups = new Map<string, AvailableItemDetail[]>()
  for (const item of filteredAvailableItems.value) {
    const key = item.inventoryName
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(item)
  }
  return groups
})

const selectedCount = computed(() => props.selectedItemIds.size)

function onAssignmentScan(value: string) {
  itemFilter.value = value
}
</script>

<template>
  <SubHeader class="mb-2">{{ t('lending.assignItems') }}</SubHeader>
  <Spinner v-if="loading"/>
  <NeutralContainer v-else-if="availableItems.length > 0" class="mb-4">
    <p class="text-sm text-[var(--text-muted)] mb-3">{{ t('lending.selectItemsToLend') }}</p>
    <div class="flex items-center gap-2 mb-3">
      <SearchInput v-model="itemFilter" :placeholder="t('inventory.manage.scanPlaceholder')" class="flex-1"/>
      <ScanButton @decoded="onAssignmentScan"/>
    </div>
    <template v-for="[groupName, items] in groupedAvailableItems" :key="groupName">
      <div class="font-medium text-sm mb-1">{{ groupName }}</div>
      <div class="flex flex-col gap-1 mb-3">
        <div v-for="item in items" :key="item.itemId"
             class="flex items-center gap-2">
          <SelectionToggleButton
              :selected="selectedItemIds.has(item.itemId)"
              size="sm"
              @toggle="emit('toggleItem', item.itemId)">
            <font-awesome-icon :icon="['fas', selectedItemIds.has(item.itemId) ? 'square-check' : 'square']" class="mr-1"/>
            <span>{{ item.internalId }}</span>
            <span class="mx-1">—</span>
            <span>{{ item.itemName }}</span>
            <span v-if="item.sizeName" class="text-[var(--text-muted)] ml-1">({{ item.sizeName }})</span>
          </SelectionToggleButton>
        </div>
      </div>
    </template>
    <div class="flex items-center justify-between mt-2">
      <span class="text-sm text-[var(--text-muted)]">{{ selectedCount }} {{ t('lending.itemsSelected') }}</span>
      <SuccessButton :icon="['fas', 'hand-holding']" :disabled="selectedCount === 0 || assigning" @click="emit('assignAndLend')">
        {{ t('lending.lendOut') }}
      </SuccessButton>
    </div>
  </NeutralContainer>
  <MutedText tag="p" size="sm" v-else>{{ t('lending.noItems') }}</MutedText>
</template>
