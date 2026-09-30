/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import type {ColumnEntry, DeletionStrategy} from '@/api/dataTracking'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'

defineProps<{
  columns: ColumnEntry[]
  strategies: readonly string[]
}>()

const strategy = defineModel<DeletionStrategy>('strategy', {required: true})

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()
const id = useId()
</script>

<template>
  <div class="rounded-theme border border-(--border) p-2 mb-2 space-y-2">
    <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
      <div>
        <label :for="`${id}-column`" class="block text-xs text-(--text-muted) mb-1">
          {{ t('adminDataTracking.detail.strategyColumn') }}
        </label>
        <SelectInput :id="`${id}-column`" v-model="strategy.column">
          <option v-for="c in columns" :key="c.name" :value="c.name">{{ c.name }}</option>
        </SelectInput>
      </div>
      <div>
        <label :for="`${id}-kind`" class="block text-xs text-(--text-muted) mb-1">
          {{ t('adminDataTracking.detail.strategyKind') }}
        </label>
        <SelectInput :id="`${id}-kind`" v-model="strategy.strategy">
          <option v-for="st in strategies" :key="st" :value="st">{{ st }}</option>
        </SelectInput>
      </div>
    </div>
    <div>
      <label :for="`${id}-reason`" class="block text-xs text-(--text-muted) mb-1">
        {{ t('adminDataTracking.detail.reason') }}
      </label>
      <TextInput :id="`${id}-reason`" v-model="strategy.reason"/>
    </div>
    <div v-if="strategy.strategy === 'RETAIN'">
      <label :for="`${id}-legal-basis`" class="block text-xs text-(--text-muted) mb-1">
        {{ t('adminDataTracking.detail.legalBasis') }}
      </label>
      <TextInput
          :id="`${id}-legal-basis`"
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
