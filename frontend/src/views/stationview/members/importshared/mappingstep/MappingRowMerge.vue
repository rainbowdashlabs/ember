/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

const { t } = useI18n()

const mergeOrder = defineModel<number>('mergeOrder', { required: true })
const mergeSeparator = defineModel<string>('mergeSeparator', { required: true })
</script>

<template>
  <div class="flex items-center gap-2 mt-2 text-xs">
    <span class="text-(--text-muted)">
      <font-awesome-icon :icon="['fas', 'link']" class="mr-1" />
      {{ t('memberImport.mergedWith') }}
    </span>
    <div class="flex items-center gap-1">
      <label class="text-(--text-muted)">{{ t('memberImport.order') }}:</label>
      <NumberInput :model-value="mergeOrder" class="w-12 text-center text-xs" @update:model-value="mergeOrder = $event ?? 0" />
    </div>
    <div class="flex items-center gap-1">
      <label class="text-(--text-muted)">{{ t('memberImport.sep') }}:</label>
      <TextInput v-model="mergeSeparator" class="w-10 text-center text-xs" />
    </div>
  </div>
</template>
