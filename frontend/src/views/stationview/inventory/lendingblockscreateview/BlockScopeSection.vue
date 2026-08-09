/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import BlockEntryTile from './BlockEntryTile.vue'
import type {Inventory} from '@/api/inventory'
import type {BlockEntry} from './types'

defineProps<{
  entries: BlockEntry[]
  availableInventories: Inventory[]
}>()

const emit = defineEmits<{
  add: []
  remove: [inventoryId: number]
  toggleAll: [entry: BlockEntry, value: boolean]
  toggleItem: [entry: BlockEntry, itemId: number]
}>()

const addInventoryId = defineModel<string>('addInventoryId', {required: true})

const {t} = useI18n()
</script>

<template>
  <div>
    <FieldLabel class="mb-2">{{ t('lending.blockScope') }}</FieldLabel>
    <MutedText v-if="entries.length === 0" tag="div" size="sm" class="mb-3">{{ t('lending.blockScopeHint') }}</MutedText>

    <div v-if="entries.length > 0" class="space-y-3 mb-3">
      <BlockEntryTile
          v-for="entry in entries"
          :key="entry.inventoryId"
          :entry="entry"
          @remove="emit('remove', $event)"
          @toggle-all="emit('toggleAll', entry, $event)"
          @toggle-item="emit('toggleItem', entry, $event)"
      />
    </div>

    <div class="flex gap-2 items-end">
      <div class="flex-1">
        <SelectInput v-model="addInventoryId">
          <option value="">{{ t('lending.selectInventory') }}</option>
          <option v-for="inv in availableInventories" :key="inv.id" :value="String(inv.id)">
            {{ inv.name }}
          </option>
        </SelectInput>
      </div>
      <SecondaryButton :icon="['fas', 'plus']" :disabled="!addInventoryId" @click="emit('add')">
        {{ t('lending.addInventory') }}
      </SecondaryButton>
    </div>
  </div>
</template>
