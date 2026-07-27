/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const managerCount = defineModel<number>({required: true})

const { t } = useI18n()

const choices = ['0', '1', '2', '3', '4']
</script>

<template>
  <div class="flex items-center gap-2">
    <FieldLabel hint>{{ t('memberImport.managerCountLabel') }}</FieldLabel>
    <SelectInput
        :model-value="String(managerCount)"
        class="w-20"
        @update:model-value="managerCount = Number($event)"
    >
      <option v-for="choice in choices" :key="choice" :value="choice">{{ choice }}</option>
    </SelectInput>
  </div>
</template>
