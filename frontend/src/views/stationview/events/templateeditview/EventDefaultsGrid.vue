/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {EventTypes} from '@/api/types'
import type {AttendanceTemplate, EventCategory} from '@/api/types'

const {t} = useI18n()

defineProps<{
  categories: EventCategory[]
  attendanceTemplates: AttendanceTemplate[]
}>()

const title = defineModel<string>('title', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const eventType = defineModel<string>('eventType', {required: true})
const attendanceTemplateId = defineModel<string>('attendanceTemplateId', {required: true})
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('eventTemplates.eventTitle') }}</FieldLabel>
      <TextInput v-model="title" :placeholder="t('eventTemplates.eventTitlePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('eventTemplates.category') }}</FieldLabel>
      <SelectInput v-model="categoryId" class="w-full">
        <option value="">{{ t('eventTemplates.noCategory') }}</option>
        <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
      </SelectInput>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('eventTemplates.eventType') }}</FieldLabel>
      <SelectInput v-model="eventType" class="w-full">
        <option value="">{{ t('eventTemplates.noDefault') }}</option>
        <option :value="EventTypes.ONE_TIME">{{ t('events.typeOneTime') }}</option>
        <option :value="EventTypes.RECURRING">{{ t('events.typeRecurring') }}</option>
        <option :value="EventTypes.MONTHLY_FIRST">{{ t('events.typeMonthlyFirst') }}</option>
        <option :value="EventTypes.QUARTERLY">{{ t('events.typeQuarterly') }}</option>
        <option :value="EventTypes.YEARLY">{{ t('events.typeYearly') }}</option>
      </SelectInput>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('eventTemplates.attendanceTemplate') }}</FieldLabel>
      <SelectInput v-model="attendanceTemplateId" class="w-full">
        <option value="">{{ t('eventTemplates.noDefault') }}</option>
        <option v-for="tpl in attendanceTemplates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
      </SelectInput>
    </div>
  </div>
</template>
