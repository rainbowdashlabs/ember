/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import {
  FILTER_LABEL_KEYS,
  STATION_FILTER_ORDER,
  StationViews,
  type StationFilter,
  type StationView,
} from './stationList'

/** Which stations the list shows, and whether as tiles or as a table. The slot sits at the end of the line. */
const filter = defineModel<StationFilter>('filter', {required: true})
const view = defineModel<StationView>('view', {required: true})

const {t} = useI18n()

const views = [
  {value: StationViews.TILES, label: 'adminStations.viewTiles', icon: ['fas', 'building']},
  {value: StationViews.TABLE, label: 'adminStations.viewTable', icon: ['fas', 'table-list']},
] as const
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-2">
    <div class="flex flex-wrap gap-2" role="radiogroup" :aria-label="t('adminStations.filterLabel')">
      <SelectionToggleButton
          v-for="choice in STATION_FILTER_ORDER"
          :key="choice"
          :selected="filter === choice"
          role="radio"
          :aria-checked="filter === choice"
          :aria-pressed="undefined"
          size="md"
          :data-testid="`station-filter-${choice}`"
          @toggle="filter = choice">
        {{ t(FILTER_LABEL_KEYS[choice]) }}
      </SelectionToggleButton>
    </div>
    <div class="flex flex-wrap items-center gap-2">
      <slot/>
      <div class="flex flex-wrap gap-2" role="radiogroup" :aria-label="t('adminStations.viewLabel')">
        <SelectionToggleButton
            v-for="choice in views"
            :key="choice.value"
            :selected="view === choice.value"
            role="radio"
            :aria-checked="view === choice.value"
            :aria-pressed="undefined"
            size="md"
            :data-testid="`station-view-${choice.value}`"
            @toggle="view = choice.value">
          <font-awesome-icon :icon="[...choice.icon]" class="mr-1"/>
          {{ t(choice.label) }}
        </SelectionToggleButton>
      </div>
    </div>
  </div>
</template>
