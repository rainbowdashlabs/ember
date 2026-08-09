/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {ColumnEntry, DeletionStrategy} from '@/api/dataTracking'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'

defineProps<{
  strategy: DeletionStrategy
  columns: ColumnEntry[]
  strategies: readonly string[]
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="rounded-theme border border-(--border) p-2 mb-2 space-y-2">
    <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
      <div>
        <label class="block text-xs text-(--text-muted) mb-1">
          {{ t('adminDataTracking.detail.strategyColumn') }}
        </label>
        <SelectInput v-model="strategy.column">
          <option v-for="c in columns" :key="c.name" :value="c.name">{{ c.name }}</option>
        </SelectInput>
      </div>
      <div>
        <label class="block text-xs text-(--text-muted) mb-1">
          {{ t('adminDataTracking.detail.strategyKind') }}
        </label>
        <SelectInput v-model="strategy.strategy">
          <option v-for="st in strategies" :key="st" :value="st">{{ st }}</option>
        </SelectInput>
      </div>
    </div>
    <div>
      <label class="block text-xs text-(--text-muted) mb-1">
        {{ t('adminDataTracking.detail.reason') }}
      </label>
      <TextInput v-model="strategy.reason"/>
    </div>
    <div v-if="strategy.strategy === 'RETAIN'">
      <label class="block text-xs text-(--text-muted) mb-1">
        {{ t('adminDataTracking.detail.legalBasis') }}
      </label>
      <TextInput
          :model-value="strategy.legalBasis ?? undefined"
          :placeholder="t('adminDataTracking.detail.legalBasisPlaceholder')"
          @update:model-value="strategy.legalBasis = $event ?? null"
      />
    </div>
    <div class="flex justify-end">
      <DeleteButton @click="emit('remove')">
        {{ t('common.delete') }}
      </DeleteButton>
    </div>
  </div>
</template>
