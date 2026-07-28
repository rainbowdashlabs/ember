/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingListField } from '@/api/waitingList'

const props = defineProps<{
  entriesCount: number
  fields: WaitingListField[]
  visibleFieldIds: Set<number>
  isMobile: boolean
  showFieldToggle: boolean
  canAdd?: boolean
}>()

const emit = defineEmits<{
  toggleField: [fieldId: number]
  toggleFieldMenu: []
  addEntry: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SubHeader>{{ t('waitingList.sectionWaiting') }} ({{ props.entriesCount }})</SubHeader>
    <div class="flex items-center gap-2 w-full sm:w-auto">
      <div class="relative flex-1 sm:flex-initial">
        <SecondaryButton :icon="['fas', 'table-columns']" :full-width="props.isMobile" @click="emit('toggleFieldMenu')">
          {{ t('waitingList.columns') }}
        </SecondaryButton>
        <div v-if="props.showFieldToggle" class="absolute right-0 top-full mt-1 z-20 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg p-2 min-w-48">
          <div v-for="field in props.fields" :key="field.id" class="flex items-center gap-2 px-2 py-1 rounded hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 cursor-pointer" @click="emit('toggleField', field.id)">
            <font-awesome-icon :icon="['fas', props.visibleFieldIds.has(field.id) ? 'square-check' : 'square']" class="text-primary" />
            <span class="text-sm">{{ field.name }}</span>
          </div>
          <div v-if="props.fields.length === 0" class="text-xs text-(--text-muted) px-2 py-1">{{ t('waitingList.noFields') }}</div>
        </div>
      </div>
      <PrimaryButton v-if="props.canAdd" :icon="['fas', 'plus']" :full-width="props.isMobile" class="flex-1 sm:flex-initial" @click="emit('addEntry')">
        {{ t('waitingList.addEntry') }}
      </PrimaryButton>
    </div>
  </div>
</template>
