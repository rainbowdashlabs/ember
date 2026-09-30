/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'

/**
 * The columns a data tracking context leaves out, picked from the table's own columns.
 */
defineProps<{
  columnOptions: { value: string; label: string; group?: string }[]
}>()

const ignored = defineModel<string[] | undefined>('ignored', {required: true})

const {t} = useI18n()
const fieldId = useId()

const selected = computed({
  get: () => ignored.value ?? [],
  set: (columns: string[]) => { ignored.value = [...columns] },
})
</script>

<template>
  <div>
    <label :for="fieldId" class="block text-xs text-(--text-muted) mb-1">
      {{ t('adminDataTracking.detail.ignoredColumns') }}
    </label>
    <MultiSelectDropdown
        :id="fieldId"
        v-model="selected"
        :options="columnOptions"
        :placeholder="t('adminDataTracking.detail.ignoredColumnsPlaceholder')"
        searchable
    />
  </div>
</template>
