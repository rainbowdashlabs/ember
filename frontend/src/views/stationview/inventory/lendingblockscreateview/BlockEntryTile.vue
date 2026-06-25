/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type {BlockEntry} from './types'

defineProps<{
  entry: BlockEntry
}>()

const emit = defineEmits<{
  remove: [inventoryId: number]
  toggleAll: [value: boolean]
  toggleItem: [itemId: number]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-between mb-2">
      <SubHeader>{{ entry.inventoryName }}</SubHeader>
      <DeleteButton @click="emit('remove', entry.inventoryId)"/>
    </div>

    <FieldLabel inline class="cursor-pointer">
      <ToggleInput :model-value="entry.allItems" @update:model-value="emit('toggleAll', $event)"/>
      {{ t('lending.blockItemAll') }}
    </FieldLabel>

    <div v-if="!entry.allItems" class="mt-3">
      <Spinner v-if="entry.loadingItems" size="sm"/>
      <div v-else-if="entry.items.length === 0">
        <MutedText size="sm">{{ t('lending.noAvailableItems') }}</MutedText>
      </div>
      <div v-else class="flex flex-wrap gap-2">
        <FieldLabel v-for="item in entry.items" :key="item.id" inline class="cursor-pointer">
          <CheckboxInput :model-value="entry.selectedItemIds.has(item.id)" @update:model-value="emit('toggleItem', item.id)"/>
          {{ item.name }}{{ item.internalId ? ` (${item.internalId})` : '' }}
        </FieldLabel>
      </div>
    </div>
  </NeutralContainer>
</template>
