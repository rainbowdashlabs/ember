/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {EventCategory} from '@/api/events'

const {t} = useI18n()

defineProps<{
  categories: EventCategory[]
  selectedIds: Set<number>
}>()

const emit = defineEmits<{
  toggle: [id: number]
}>()
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('events.exportCategories') }}</FieldLabel>
    <div class="flex flex-wrap gap-2">
      <SelectionToggleButton
          v-for="cat in categories"
          :key="cat.id"
          :selected="selectedIds.has(cat.id)"
          @toggle="emit('toggle', cat.id)"
      >
        {{ cat.name }}
      </SelectionToggleButton>
    </div>
  </div>
</template>
