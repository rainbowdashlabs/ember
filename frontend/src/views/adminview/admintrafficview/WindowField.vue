/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'

/**
 * The span of time a monitoring page is drawn over, as a labelled choice. Traffic offers hours,
 * the statistics offer days, months and everything; both read the same field.
 */
defineProps<{
  options: ReadonlyArray<{value: string; label: string}>
}>()

const span = defineModel<string>({required: true})

const {t} = useI18n()
</script>

<template>
  <label class="flex flex-col gap-1 text-sm">
    <span class="text-(--text-muted)">{{ t('traffic.controls.window') }}</span>
    <SelectInput v-model="span">
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </SelectInput>
  </label>
</template>
