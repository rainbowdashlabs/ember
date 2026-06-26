/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()

const separator = defineModel<string>('separator', { required: true })
const managerCount = defineModel<number>('managerCount', { required: true })

defineProps<{
  fileName: string
  csvText: string
  loading: boolean
}>()

const emit = defineEmits<{
  fileUpload: [file: File]
  parse: []
}>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('memberImport.upload') }}</SubHeader>
    <div class="flex items-center gap-4 flex-wrap">
      <FileUploadButton accept=".csv,.txt" @select="emit('fileUpload', $event)">
        {{ t('memberImport.chooseFile') }}
      </FileUploadButton>
      <span v-if="fileName" class="text-sm">
        <font-awesome-icon :icon="['fas', 'check']" class="text-success mr-1" />
        {{ fileName }} ({{ csvText.split('\n').length - 1 }} {{ t('memberImport.rows') }})
      </span>
    </div>
    <div class="flex items-center gap-6 flex-wrap">
      <div class="flex items-center gap-2">
        <label class="text-sm font-medium">{{ t('memberImport.separator') }}</label>
        <SelectInput v-model="separator" class="w-20">
          <option value=";">;</option>
          <option value=",">,</option>
          <option value="&#9;">Tab</option>
        </SelectInput>
      </div>
      <div class="flex items-center gap-2">
        <label class="text-sm font-medium">{{ t('memberImport.managerCountLabel') }}</label>
        <SelectInput :model-value="String(managerCount)" class="w-20" @update:model-value="managerCount = Number($event)">
          <option value="0">0</option>
          <option value="1">1</option>
          <option value="2">2</option>
          <option value="3">3</option>
          <option value="4">4</option>
        </SelectInput>
      </div>
    </div>
    <PrimaryButton :disabled="!csvText || loading" @click="emit('parse')">
      {{ loading ? t('common.loading') : t('memberImport.next') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
