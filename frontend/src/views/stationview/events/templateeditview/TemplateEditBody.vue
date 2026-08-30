/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EventFieldList from '../eventshared/EventFieldList.vue'
import EventReminderEditor from '../eventshared/EventReminderEditor.vue'
import EventDefaultsSection from './EventDefaultsSection.vue'
import TemplateAudienceSection from './TemplateAudienceSection.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import type {AttendanceTemplate, AttendanceTemplateField} from '@/api/attendance'
import type {EventCategory, EventFieldEntry} from '@/api/events'
import type {MemberGroup, UserTag} from '@/api/types'

/**
 * Everything a template is edited through, once it has been loaded.
 *
 * <p>Its own component because the view around it is the loading, the error and the way back, and
 * the template itself is five sections that have nothing to do with any of those.
 */
defineProps<{
  categories: EventCategory[]
  attendanceTemplates: AttendanceTemplate[]
  sheetFields: AttendanceTemplateField[]
  groups: MemberGroup[]
  tags: UserTag[]
  save: () => Promise<void> | void
}>()

const name = defineModel<string>('name', {required: true})
const title = defineModel<string>('title', {required: true})
const description = defineModel<string>('description', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const eventType = defineModel<string>('eventType', {required: true})
const attendanceTemplateId = defineModel<string>('attendanceTemplateId', {required: true})
const requiresRegistration = defineModel<boolean>('requiresRegistration', {required: true})
const requiresConfirmation = defineModel<boolean>('requiresConfirmation', {required: true})
const registrationLimit = defineModel<number | undefined>('registrationLimit')
const restriction = defineModel<RestrictionSelection>('restriction', {required: true})
const viewRestriction = defineModel<RestrictionSelection>('viewRestriction', {required: true})
const reminderDays = defineModel<number[]>('reminderDays', {required: true})
const fields = defineModel<EventFieldEntry[]>('fields', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('eventTemplates.name') }}</SubHeader>
    <TextInput v-model="name" :placeholder="t('eventTemplates.namePlaceholder')"/>
  </NeutralContainer>

  <EventDefaultsSection
      v-model:title="title"
      v-model:description="description"
      v-model:category-id="categoryId"
      v-model:event-type="eventType"
      v-model:attendance-template-id="attendanceTemplateId"
      v-model:requires-registration="requiresRegistration"
      v-model:requires-confirmation="requiresConfirmation"
      v-model:registration-limit="registrationLimit"
      :categories="categories"
      :attendance-templates="attendanceTemplates"
  />

  <TemplateAudienceSection
      v-model:restriction="restriction"
      v-model:view-restriction="viewRestriction"
      :groups="groups"
      :tags="tags"
  />

  <NeutralContainer>
    <EventReminderEditor v-model="reminderDays"/>
  </NeutralContainer>

  <NeutralContainer class="space-y-4">
    <EventFieldList
        v-model:fields="fields"
        :attendance-fields="sheetFields"
        :groups="groups"
        :tags="tags"
        :value-label="t('eventFields.defaultValue')"
        show-value
    />
  </NeutralContainer>

  <SaveButton :disabled="!name.trim()" :action="save"/>
</template>
