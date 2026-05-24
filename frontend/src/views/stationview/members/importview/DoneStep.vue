/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

export interface ImportResult {
  imported: number
  skipped: number
  errors: string[]
}

defineProps<{
  result: ImportResult
}>()

const emit = defineEmits<{
  startOver: []
  toList: []
}>()

const { t } = useI18n()
</script>

<template>
  <SuccessContainer class="space-y-4">
    <div class="flex items-center gap-3">
      <font-awesome-icon :icon="['fas', 'check-circle']" class="text-2xl text-success" />
      <span class="text-lg font-semibold">{{ t('memberImport.doneTitle') }}</span>
    </div>
    <div class="text-sm space-y-1">
      <p>{{ t('memberImport.doneImported', { count: result.imported }) }}</p>
      <p v-if="result.skipped > 0" class="text-(--text-muted)">{{ t('memberImport.doneSkipped', { count: result.skipped }) }}</p>
      <ul v-if="result.errors.length > 0" class="mt-2 space-y-1">
        <li v-for="(err, idx) in result.errors" :key="idx" class="text-error text-xs">{{ err }}</li>
      </ul>
    </div>
    <div class="flex gap-3">
      <PrimaryButton @click="emit('toList')">
        <font-awesome-icon :icon="['fas', 'users']" class="mr-1" />
        {{ t('memberImport.doneGoToList') }}
      </PrimaryButton>
      <SecondaryButton @click="emit('startOver')">
        <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1" />
        {{ t('memberImport.doneStartOver') }}
      </SecondaryButton>
    </div>
  </SuccessContainer>
</template>
