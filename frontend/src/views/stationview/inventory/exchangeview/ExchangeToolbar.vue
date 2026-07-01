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

const { t } = useI18n()

defineProps<{
  exportMode: boolean
  exporting: boolean
  selectedCount: number
  canExport: boolean
}>()

const emit = defineEmits<{
  (e: 'export'): void
  (e: 'cancel-export'): void
  (e: 'enter-export'): void
  (e: 'create'): void
}>()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SubHeader>{{ t('exchanges.title') }}</SubHeader>
    <div class="flex items-center gap-2">
      <template v-if="exportMode">
        <SecondaryButton :icon="['fas', 'download']" :disabled="exporting || selectedCount === 0" @click="emit('export')">
          {{ exporting ? t('common.loading') : t('exchanges.download') }} ({{ selectedCount }})
        </SecondaryButton>
        <SecondaryButton @click="emit('cancel-export')">{{ t('common.cancel') }}</SecondaryButton>
      </template>
      <template v-else>
        <SecondaryButton :icon="['fas', 'file-export']" v-if="canExport" @click="emit('enter-export')">
          {{ t('exchanges.export') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']" @click="emit('create')">
          {{ t('exchanges.create') }}
        </PrimaryButton>
      </template>
    </div>
  </div>
</template>
