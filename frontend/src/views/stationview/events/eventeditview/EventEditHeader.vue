/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {EventTemplate} from '@/api/events'

defineProps<{
  isEdit: boolean
  eventTemplates: EventTemplate[]
}>()

const emit = defineEmits<{
  'apply-template': [value: string | undefined]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SubHeader>{{ isEdit ? t('events.editEvent') : t('events.addEvent') }}</SubHeader>
    <div v-if="eventTemplates.length > 0" class="flex items-center gap-2">
      <SelectInput model-value="" class="w-48 text-sm" @update:model-value="emit('apply-template', $event)">
        <option value="" disabled>{{ t('eventTemplates.loadTemplate') }}</option>
        <option v-for="et in eventTemplates" :key="et.id" :value="String(et.id)">{{ et.name }}</option>
      </SelectInput>
    </div>
  </div>
</template>
