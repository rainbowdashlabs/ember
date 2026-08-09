/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const props = defineProps<{
  firstname: string
  lastname: string
}>()

const emit = defineEmits<{
  (e: 'update:firstname', value: string): void
  (e: 'update:lastname', value: string): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.firstname') }} <span class="text-error">*</span></FieldLabel>
      <TextInput :model-value="firstname" @update:model-value="emit('update:firstname', $event ?? '')" :placeholder="t('waitingList.firstnamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
      <TextInput :model-value="lastname" @update:model-value="emit('update:lastname', $event ?? '')" :placeholder="t('waitingList.lastnamePlaceholder')"/>
    </div>
  </div>
</template>
