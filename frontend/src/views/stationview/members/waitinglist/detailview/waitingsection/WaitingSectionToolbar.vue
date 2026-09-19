/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ColumnPickerButton from '@/components/table/ColumnPickerButton.vue'
import type { ColumnPickerOption } from '@/components/table/columns'

const props = defineProps<{
  entriesCount: number
  /** The questions the list may show as columns, each ticked where it does. */
  columnOptions: ColumnPickerOption[]
  isMobile: boolean
  canAdd?: boolean
}>()

const emit = defineEmits<{
  toggleColumn: [key: string | number]
  addEntry: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SubHeader>{{ t('waitingList.sectionWaiting') }} ({{ props.entriesCount }})</SubHeader>
    <div class="flex items-center gap-2 w-full sm:w-auto">
      <div class="flex-1 sm:flex-initial">
        <ColumnPickerButton
          :empty-label="t('waitingList.noFields')"
          :full-width="props.isMobile"
          :options="props.columnOptions"
          @toggle="(key) => emit('toggleColumn', key)"
        />
      </div>
      <PrimaryButton v-if="props.canAdd" :icon="['fas', 'plus']" :full-width="props.isMobile" class="flex-1 sm:flex-initial" @click="emit('addEntry')">
        {{ t('waitingList.addEntry') }}
      </PrimaryButton>
    </div>
  </div>
</template>
