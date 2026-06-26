/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import FieldHint from '@/components/typography/FieldHint.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

const separator = defineModel<string>('separator', {required: true})

const emit = defineEmits<{
  fileSelected: [event: Event]
  separatorChanged: []
}>()

const { t } = useI18n()

function onSeparatorUpdate(value: string | null | undefined) {
  separator.value = value ?? ','
  emit('separatorChanged')
}
</script>

<template>
  <div class="flex items-center gap-2">
    <label class="inline-flex items-center gap-2 px-3 py-1.5 text-sm rounded-lg font-medium cursor-pointer bg-bg-light-accent dark:bg-bg-dark-accent hover:brightness-110 transition-all">
      <font-awesome-icon :icon="['fas', 'upload']" />
      {{ t('quiz.csv.selectFile') }}
      <input type="file" accept=".csv,.tsv,.txt" class="hidden" @change="emit('fileSelected', $event)" />
    </label>
    <div class="flex items-center gap-2">
      <FieldHint>{{ t('quiz.csv.separator') }}</FieldHint>
      <SelectInput :model-value="separator" class="w-24" @update:model-value="onSeparatorUpdate">
        <option value=",">,</option>
        <option value=";">;</option>
        <option value="&#9">Tab</option>
      </SelectInput>
    </div>
  </div>
</template>
