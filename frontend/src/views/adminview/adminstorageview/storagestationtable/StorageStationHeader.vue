/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

type SortField = 'name' | 'usage' | 'percent'

defineProps<{
  sortBy: SortField
  sortDesc: boolean
}>()

const emit = defineEmits<{
  toggle: [field: SortField]
}>()

const {t} = useI18n()
</script>

<template>
  <thead>
  <tr class="border-b border-(--border)">
    <th class="text-left p-2 cursor-pointer" @click="emit('toggle', 'name')">
      {{ t('storageMonitoring.stationName') }}
      <font-awesome-icon v-if="sortBy === 'name'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
    </th>
    <th class="text-left p-2 cursor-pointer min-w-50" @click="emit('toggle', 'usage')">
      {{ t('storageMonitoring.usage') }}
      <font-awesome-icon v-if="sortBy === 'usage'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
    </th>
    <th class="text-right p-2 cursor-pointer" @click="emit('toggle', 'percent')">
      {{ t('storageMonitoring.quota') }}
      <font-awesome-icon v-if="sortBy === 'percent'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
    </th>
    <th class="text-center p-2">{{ t('storageMonitoring.status') }}</th>
    <th class="text-left p-2">{{ t('storageMonitoring.preset') }}</th>
    <th class="text-right p-2">{{ t('storageMonitoring.actions') }}</th>
  </tr>
  </thead>
</template>
