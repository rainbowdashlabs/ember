/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'

const separator = defineModel<string>('separator', {required: true})

defineProps<{
  fileName: string | null
}>()

const emit = defineEmits<{
  fileSelected: [file: File]
}>()

const {t} = useI18n()

function onFileSelect(file: File) {
  emit('fileSelected', file)
}
</script>

<template>
  <NeutralContainer class="space-y-4 mb-4">
    <SectionHeader>1. {{ t('quiz.csv.selectFile') }}</SectionHeader>
    <div class="flex items-center gap-4 flex-wrap">
      <FileUploadButton accept=".csv,.tsv,.txt" @select="onFileSelect">
        {{ t('quiz.csv.selectFile') }}
      </FileUploadButton>
      <span v-if="fileName" class="text-sm text-(--text-muted)">{{ fileName }}</span>
      <div class="flex items-center gap-2">
        <FieldHint>{{ t('quiz.csv.separator') }}</FieldHint>
        <SelectInput :model-value="separator" class="w-24" @update:model-value="separator = $event ?? separator">
          <option value=",">,</option>
          <option value=";">;</option>
          <option value="&#9">Tab</option>
        </SelectInput>
      </div>
    </div>
  </NeutralContainer>
</template>
