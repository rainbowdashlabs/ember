/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'

const { t } = useI18n()

defineProps<{
  exportMode: boolean
  showMemberColumn: boolean
  canManageExchanges: boolean
  allSelected: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-select-all'): void
}>()
</script>

<template>
  <THead>
    <th v-if="exportMode" class="px-1 py-2 w-8">
      <CheckboxInput :model-value="allSelected" @update:model-value="emit('toggle-select-all')" />
    </th>
    <Th v-if="showMemberColumn">{{ t('exchanges.colMember') }}</Th>
    <Th>{{ t('exchanges.colInventory') }}</Th>
    <Th v-if="canManageExchanges">{{ t('exchanges.colType') }}</Th>
    <Th>{{ t('exchanges.colOldSize') }}</Th>
    <Th>{{ t('exchanges.colNewSize') }}</Th>
    <Th>{{ t('exchanges.colStatus') }}</Th>
    <Th>{{ t('exchanges.colReason') }}</Th>
    <Th>{{ t('exchanges.colDate') }}</Th>
    <th class="px-3 py-2"></th>
  </THead>
</template>
