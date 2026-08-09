/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EventDefaultsGrid from './EventDefaultsGrid.vue'
import type {AttendanceTemplate} from '@/api/attendance'
import type {EventCategory} from '@/api/events'

const {t} = useI18n()

defineProps<{
  categories: EventCategory[]
  attendanceTemplates: AttendanceTemplate[]
}>()

const title = defineModel<string>('title', {required: true})
const description = defineModel<string>('description', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const eventType = defineModel<string>('eventType', {required: true})
const attendanceTemplateId = defineModel<string>('attendanceTemplateId', {required: true})
const requiresRegistration = defineModel<boolean>('requiresRegistration', {required: true})
const requiresConfirmation = defineModel<boolean>('requiresConfirmation', {required: true})
const registrationLimit = defineModel<number | undefined>('registrationLimit')
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('eventTemplates.eventDefaults') }}</SubHeader>

    <EventDefaultsGrid
        v-model:title="title"
        v-model:category-id="categoryId"
        v-model:event-type="eventType"
        v-model:attendance-template-id="attendanceTemplateId"
        :categories="categories"
        :attendance-templates="attendanceTemplates"
    />

    <div class="space-y-1">
      <FieldLabel>{{ t('eventTemplates.eventDescription') }}</FieldLabel>
      <TextAreaInput v-model="description" :rows="3"/>
    </div>

    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">{{ t('events.requiresRegistration') }}</span>
      <ToggleInput v-model="requiresRegistration"/>
    </div>
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</span>
      <ToggleInput v-model="requiresConfirmation"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('events.registrationLimit') }}</FieldLabel>
      <NumberInput v-model="registrationLimit" :placeholder="t('events.registrationLimitHint')"/>
    </div>
  </NeutralContainer>
</template>
