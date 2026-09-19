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
import { useBreakpoint } from '@/composables/useBreakpoint'

const props = defineProps<{
  entriesCount: number
  /** The questions the list may show as columns, each ticked where it does. */
  columnOptions: ColumnPickerOption[]
  canAdd?: boolean
}>()

const emit = defineEmits<{
  setColumns: [keys: (string | number)[], visible: boolean]
  addEntry: []
}>()

const { t } = useI18n()
const { isMobile } = useBreakpoint()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SubHeader>{{ t('waitingList.sectionWaiting') }} ({{ props.entriesCount }})</SubHeader>
    <div class="flex items-center gap-2 w-full sm:w-auto">
      <div class="flex-1 sm:flex-initial">
        <ColumnPickerButton
          :empty-label="t('waitingList.noFields')"
          :full-width="isMobile"
          :options="props.columnOptions"
          @set-visible="(keys, visible) => emit('setColumns', keys, visible)"
        />
      </div>
      <PrimaryButton v-if="props.canAdd" :icon="['fas', 'plus']" :full-width="isMobile" class="flex-1 sm:flex-initial" @click="emit('addEntry')">
        {{ t('waitingList.addEntry') }}
      </PrimaryButton>
    </div>
  </div>
</template>
