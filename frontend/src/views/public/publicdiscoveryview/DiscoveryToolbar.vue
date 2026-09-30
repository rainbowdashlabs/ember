/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

/** The search over every listed station, local and remote, and the switch between list and map. */
const search = defineModel<string>('search', {required: true})
const tab = defineModel<'list' | 'map'>('tab', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
    <SearchInput v-model="search" :placeholder="t('discovery.searchPlaceholder')" class="sm:flex-1"/>
    <div class="flex justify-end gap-1">
      <SelectionToggleButton :selected="tab === 'list'" @toggle="tab = 'list'">
        <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
        {{ t('stationDiscovery.listTab') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="tab === 'map'" @toggle="tab = 'map'">
        <font-awesome-icon :icon="['fas', 'map-location-dot']" class="mr-1"/>
        {{ t('stationDiscovery.mapTab') }}
      </SelectionToggleButton>
    </div>
  </div>
</template>
