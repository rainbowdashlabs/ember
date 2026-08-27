/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { TargetValue } from '../useMemberCsvImport'

const { t } = useI18n()

/**
 * What a value in the file should become in Ember.
 *
 * <p>Where the question it answers has a fixed set of answers, those are offered rather than typed:
 * a target spelled differently from the answer it is meant to be matches nothing, and nothing is what
 * the reader sees, because the value simply arrives as written. Free text stays for the questions
 * that have no fixed answers.
 */
defineProps<{
  entries: Array<{ from: string; to: string }>
  /** The answers this question allows. Empty where it allows anything. */
  targetValues: TargetValue[]
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
        <div v-for="(entry, ei) in entries" :key="ei" data-testid="value-map-row" class="grid grid-cols-2 gap-2">
          <TextInput v-model="entry.from" :placeholder="t('memberImport.csvValue')" />
          <SelectInput v-if="targetValues.length > 0" v-model="entry.to" data-testid="value-map-target">
            <option value="">{{ t('memberImport.targetValueNone') }}</option>
            <option v-for="value in targetValues" :key="value.value" :value="value.value">{{ value.label }}</option>
          </SelectInput>
          <TextInput v-else v-model="entry.to" :placeholder="t('memberImport.targetValue')" />
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
