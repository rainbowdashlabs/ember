/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import BlockDateRangeSection from './BlockDateRangeSection.vue'
import BlockScopeSection from './BlockScopeSection.vue'
import type {Inventory} from '@/api/types'
import type {BlockEntry} from './types'

defineProps<{
  entries: BlockEntry[]
  availableInventories: Inventory[]
  saving: boolean
}>()

const emit = defineEmits<{
  add: []
  remove: [inventoryId: number]
  toggleAll: [entry: BlockEntry, value: boolean]
  toggleItem: [entry: BlockEntry, itemId: number]
  cancel: []
  create: []
}>()

const blockFrom = defineModel<string>('blockFrom', {required: true})
const blockTo = defineModel<string>('blockTo', {required: true})
const reason = defineModel<string>('reason', {required: true})
const addInventoryId = defineModel<string>('addInventoryId', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-5 max-w-2xl">
    <BlockDateRangeSection
        v-model:block-from="blockFrom"
        v-model:block-to="blockTo"
        v-model:reason="reason"
    />

    <BlockScopeSection
        v-model:add-inventory-id="addInventoryId"
        :entries="entries"
        :available-inventories="availableInventories"
        @add="emit('add')"
        @remove="emit('remove', $event)"
        @toggle-all="(entry, value) => emit('toggleAll', entry, value)"
        @toggle-item="(entry, itemId) => emit('toggleItem', entry, itemId)"
    />

    <div class="flex justify-end gap-2">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="saving || !blockFrom || !blockTo" @click="emit('create')">
        {{ t('common.save') }}
      </PrimaryButton>
    </div>
  </div>
</template>
