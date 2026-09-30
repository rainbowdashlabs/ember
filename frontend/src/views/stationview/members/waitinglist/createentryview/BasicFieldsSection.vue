/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

defineProps<{
  firstname: string
  lastname: string
}>()

const emit = defineEmits<{
  'update:firstname': [value: string]
  'update:lastname': [value: string]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.firstname') }} <span class="text-error">*</span></FieldLabel>
      <TextInput :model-value="firstname" :placeholder="t('waitingList.firstnamePlaceholder')" @update:model-value="emit('update:firstname', $event ?? '')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
      <TextInput :model-value="lastname" :placeholder="t('waitingList.lastnamePlaceholder')" @update:model-value="emit('update:lastname', $event ?? '')"/>
    </div>
  </div>
</template>
