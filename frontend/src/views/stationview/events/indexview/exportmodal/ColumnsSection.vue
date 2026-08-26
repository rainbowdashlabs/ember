/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DragList from '@/components/input/DragList.vue'
import SelectedColumnRow from './SelectedColumnRow.vue'

/**
 * Column definition used by the export PDF column picker.
 */
export interface ExportColumn {
  key: string
  label: string
  isExtra?: boolean
}

const {t} = useI18n()

defineProps<{
  selectedColumns: ExportColumn[]
  availableColumns: ExportColumn[]
  availableExtraFields: string[]
}>()

const emit = defineEmits<{
  add: [col: ExportColumn]
  addField: [name: string]
  remove: [index: number]
  reorder: [fromIndex: number, toIndex: number]
}>()
</script>

<template>
  <div class="space-y-3">
    <FieldLabel>{{ t('events.exportColumns') }}</FieldLabel>

    <MutedText tag="div" size="sm" class="py-2 text-center" v-if="selectedColumns.length === 0">
      {{ t('events.exportNoColumns') }}
    </MutedText>
    <DragList
        :items="selectedColumns"
        :key-fn="(col) => col.key"
        class="space-y-1"
        @reorder="(from, to) => emit('reorder', from, to)"
    >
      <template #default="{item: col, index}">
        <SelectedColumnRow :label="col.label" :index="index" @remove="emit('remove', index)"/>
      </template>
    </DragList>

    <div v-if="availableColumns.length > 0" class="flex flex-wrap gap-2">
      <SecondaryButton
          v-for="col in availableColumns"
          :key="col.key"
          class="!text-xs !border !border-dashed !border-bg-light-accent dark:!border-bg-dark-accent !text-(--text-muted) hover:!border-primary hover:!text-primary !bg-transparent"
          @click="emit('add', col)"
      >
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1 h-3 w-3"/>
        {{ col.label }}
      </SecondaryButton>
    </div>

    <div v-if="availableExtraFields.length > 0" class="space-y-1">
      <span class="text-xs text-(--text-muted)">{{ t('events.exportExtraFields') }}</span>
      <div class="flex flex-wrap gap-2">
        <SecondaryButton
            v-for="name in availableExtraFields"
            :key="name"
            class="!text-xs !border !border-dashed !border-secondary/50 !text-secondary hover:!border-secondary hover:!bg-secondary/10 !bg-transparent"
            @click="emit('addField', name)"
        >
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1 h-3 w-3"/>
          {{ name }}
        </SecondaryButton>
      </div>
    </div>
  </div>
</template>
