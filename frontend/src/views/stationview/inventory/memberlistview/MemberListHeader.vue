/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

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
  (e: 'export'): void
}>()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SubHeader>{{ t('inventoryMembers.title') }}</SubHeader>
    <ButtonRow align="end">
      <template v-if="exportMode">
        <SecondaryButton
            :icon="['fas', 'download']"
            :disabled="exporting || selectedCount === 0"
            @click="emit('export')"
        >
          {{ exporting ? t('common.loading') : t('common.export') }} ({{ selectedCount }})
        </SecondaryButton>
        <SecondaryButton @click="emit('cancel-export')">{{ t('common.cancel') }}</SecondaryButton>
      </template>
      <template v-else>
        <PrimaryButton :icon="['fas', 'file-export']" v-if="hasMembers" @click="emit('enter-export')">
          {{ t('inventoryMembers.export') }}
        </PrimaryButton>
      </template>
    </ButtonRow>
  </div>
</template>
