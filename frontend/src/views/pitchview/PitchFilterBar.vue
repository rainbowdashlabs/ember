/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {PitchFilters} from './pitchTypes'

/** The filter row of the lists: search, a select and a switch, laid out as the application does. */
defineProps<{
  filters: PitchFilters
}>()
</script>

<template>
  <div class="flex flex-wrap items-end gap-3">
    <div v-if="filters.search" class="min-w-40 flex-1 space-y-1">
      <FieldLabel>Suche</FieldLabel>
      <SearchInput :model-value="''" :placeholder="filters.search"/>
    </div>
    <div v-if="filters.select" class="w-44 space-y-1">
      <FieldLabel>{{ filters.select.label }}</FieldLabel>
      <SelectInput :model-value="filters.select.value">
        <option :value="filters.select.value">{{ filters.select.value }}</option>
      </SelectInput>
    </div>
    <div v-if="filters.toggle" class="space-y-1">
      <FieldLabel>{{ filters.toggle }}</FieldLabel>
      <div class="flex items-center px-3 py-2">
        <ToggleInput :model-value="false" :aria-label="filters.toggle"/>
      </div>
    </div>
  </div>
</template>
