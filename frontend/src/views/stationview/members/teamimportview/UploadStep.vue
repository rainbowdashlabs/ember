/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  fileName: string
  csvText: string
  separator: string
  loading: boolean
}>()

defineEmits<{
  (e: 'file-upload', file: File): void
  (e: 'update:separator', value: string): void
  (e: 'parse'): void
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('memberImport.upload') }}</SubHeader>
    <div class="flex items-center gap-4 flex-wrap">
      <FileUploadButton accept=".csv,.txt" @select="$emit('file-upload', $event)">
        {{ t('memberImport.chooseFile') }}
      </FileUploadButton>
      <span v-if="fileName" class="text-sm">
        <font-awesome-icon :icon="['fas', 'check']" class="text-success mr-1" />
        {{ fileName }} ({{ csvText.split('\n').length - 1 }} {{ t('memberImport.rows') }})
      </span>
    </div>
    <div class="flex items-center gap-2">
      <label class="text-sm font-medium">{{ t('memberImport.separator') }}</label>
      <SelectInput :model-value="separator" class="w-20" @update:model-value="$emit('update:separator', String($event ?? ';'))">
        <option value=";">;</option>
        <option value=",">,</option>
        <option value="&#9;">Tab</option>
      </SelectInput>
    </div>
    <PrimaryButton :disabled="!csvText || loading" @click="$emit('parse')">
      {{ loading ? t('common.loading') : t('memberImport.next') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
