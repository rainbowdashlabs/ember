/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()

defineProps<{
  entries: Array<{ from: string; to: string }>
}>()

defineEmits<{
  close: []
  save: []
  add: []
}>()
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="$emit('close')">
    <div class="bg-bg-light dark:bg-bg-dark rounded-lg p-6 w-full max-w-md space-y-4 shadow-lg">
      <SubHeader>{{ t('memberImport.valueMapTitle') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('memberImport.valueMapHint') }}</p>
      <div class="space-y-2">
        <div v-for="(entry, ei) in entries" :key="ei" class="grid grid-cols-2 gap-2">
          <TextInput v-model="entry.from" :placeholder="t('memberImport.csvValue')" />
          <TextInput v-model="entry.to" :placeholder="t('memberImport.targetValue')" />
        </div>
      </div>
      <SecondaryButton @click="$emit('add')">+ {{ t('memberImport.addRow') }}</SecondaryButton>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="$emit('close')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton @click="$emit('save')">{{ t('common.save') }}</PrimaryButton>
      </div>
    </div>
  </div>
</template>
