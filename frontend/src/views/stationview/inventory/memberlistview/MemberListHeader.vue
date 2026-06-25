/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const { t } = useI18n()

defineProps<{
  exportMode: boolean
  exporting: boolean
  selectedCount: number
  hasMembers: boolean
}>()

const emit = defineEmits<{
  (e: 'enter-export'): void
  (e: 'cancel-export'): void
  (e: 'export-csv'): void
  (e: 'export-pdf'): void
}>()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SectionHeader>{{ t('inventoryMembers.title') }}</SectionHeader>
    <div class="flex items-center gap-2">
      <template v-if="exportMode">
        <SecondaryButton :icon="['fas', 'download']" :disabled="exporting || selectedCount === 0" @click="emit('export-csv')">
          CSV ({{ selectedCount }})
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'download']" :disabled="exporting || selectedCount === 0" @click="emit('export-pdf')">
          {{ exporting ? t('common.loading') : 'PDF' }} ({{ selectedCount }})
        </SecondaryButton>
        <SecondaryButton @click="emit('cancel-export')">{{ t('common.cancel') }}</SecondaryButton>
      </template>
      <template v-else>
        <PrimaryButton :icon="['fas', 'file-export']" v-if="hasMembers" @click="emit('enter-export')">
          {{ t('inventoryMembers.export') }}
        </PrimaryButton>
      </template>
    </div>
  </div>
</template>
