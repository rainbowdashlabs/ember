/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EventDefaultsGrid from './EventDefaultsGrid.vue'
import type {AttendanceTemplate, EventCategory} from '@/api/generated/schema'

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
      <MarkdownEditor v-model="description" :placeholder="t('events.descriptionPlaceholder')"/>
    </div>
  </NeutralContainer>
</template>
