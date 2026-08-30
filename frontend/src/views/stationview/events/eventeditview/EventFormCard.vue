/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EventFormPanel from '../eventshared/EventFormPanel.vue'
import EventReminderEditor from '../eventshared/EventReminderEditor.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import type {AttendanceTemplate, AttendanceTemplateField} from '@/api/attendance'
import type {EventCategory, EventFieldEntry} from '@/api/events'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'

defineProps<{
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  attendanceFields: AttendanceTemplateField[]
  groups: MemberGroup[]
  tags: UserTag[]
  allMembers: StationMember[]
  groupMembers: Map<number, StationMember[]>
}>()

const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const templateId = defineModel<string>('templateId', {required: true})
const eventType = defineModel<string>('eventType', {required: true})
const dayOfWeek = defineModel<string>('dayOfWeek', {required: true})
const startTime = defineModel<string>('startTime', {required: true})
const endTime = defineModel<string>('endTime', {required: true})
const repeatUntil = defineModel<string>('repeatUntil', {required: true})
const repeatCount = defineModel<number | undefined>('repeatCount')
const requiresRegistration = defineModel<boolean>('requiresRegistration', {required: true})
const requiresConfirmation = defineModel<boolean>('requiresConfirmation', {required: true})
const hasDeadline = defineModel<boolean>('hasDeadline', {required: true})
const registrationDeadline = defineModel<string>('registrationDeadline', {required: true})
const registrationLimit = defineModel<number | undefined>('registrationLimit')
const minRegistrations = defineModel<number | undefined>('minRegistrations')
const hasThreshold = defineModel<boolean>('hasThreshold', {required: true})
const thresholdDate = defineModel<string>('thresholdDate', {required: true})
const registrationCloseDays = defineModel<number | undefined>('registrationCloseDays')
const restriction = defineModel<RestrictionSelection>('restriction', {required: true})
const viewRestriction = defineModel<RestrictionSelection>('viewRestriction', {required: true})
const fields = defineModel<EventFieldEntry[]>('fields', {required: true})
const reminders = defineModel<number[]>('reminders', {required: true})
</script>

<template>
  <NeutralContainer>
    <EventFormPanel
        v-model:name="name"
        v-model:description="description"
        v-model:category-id="categoryId"
        v-model:template-id="templateId"
        v-model:event-type="eventType"
        v-model:day-of-week="dayOfWeek"
        v-model:start-time="startTime"
        v-model:end-time="endTime"
        v-model:repeat-until="repeatUntil"
        v-model:repeat-count="repeatCount"
        v-model:requires-registration="requiresRegistration"
        v-model:requires-confirmation="requiresConfirmation"
        v-model:has-deadline="hasDeadline"
        v-model:registration-deadline="registrationDeadline"
        v-model:registration-limit="registrationLimit"
        v-model:min-registrations="minRegistrations"
        v-model:has-threshold="hasThreshold"
        v-model:threshold-date="thresholdDate"
        v-model:registration-close-days="registrationCloseDays"
        v-model:restriction="restriction"
        v-model:view-restriction="viewRestriction"
        v-model:fields="fields"
        :categories="categories"
        :templates="templates"
        :attendance-fields="attendanceFields"
        :groups="groups"
        :tags="tags"
        :all-members="allMembers"
        :group-members="groupMembers"
        show-schedule
        show-value
    >
      <template #after-schedule>
        <EventReminderEditor v-model="reminders"/>
      </template>
    </EventFormPanel>
  </NeutralContainer>
</template>
