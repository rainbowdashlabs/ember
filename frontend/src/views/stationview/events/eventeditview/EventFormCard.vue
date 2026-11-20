/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FormSection from '@/components/container/FormSection.vue'
import EventFormPanel from '../eventshared/EventFormPanel.vue'
import EventReminderEditor from '../eventshared/EventReminderEditor.vue'
import {remindersSummary} from '../eventshared/sectionSummaries'
import type {RestrictionSelection} from '@/api/types'
import type {
    AttendanceTemplate,
    AttendanceTemplateField,
    EventCategory,
    EventFieldEntry,
    MemberGroup,
    QueueOrder,
    RegistrationDeadline,
    UserTag,
} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'

defineProps<{
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  attendanceFields: AttendanceTemplateField[]
  groups: MemberGroup[]
  tags: UserTag[]
  allMembers: MemberLike[]
  groupMembers: Map<number, MemberLike[]>
  /** Whether the appointment is a part of an event group, which owns its sign-up and audiences. */
  eventGroupPart?: boolean
  /** Whether questions are asked, which hold sign-up on. */
  signUpHeld?: boolean
  /**
   * The settings the event group of a part hands down, which the inherited settings card shows: the
   * form offers its own control for one only once the part overrides it.
   */
  followsGroup?: {category: boolean; template: boolean; reminders: boolean}
  /** The folded sections that hold something the reader has to see now, by key, which opens them. */
  attention?: readonly string[]
  /** The station's time zone, which the deadline of the next date is worked out on. */
  timeZone?: string | null
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
const registrationDeadline = defineModel<RegistrationDeadline>('registrationDeadline', {required: true})
const registrationLimit = defineModel<number | undefined>('registrationLimit')
const queueOrder = defineModel<QueueOrder>('queueOrder')
const minRegistrations = defineModel<number | undefined>('minRegistrations')
const thresholdDays = defineModel<number | undefined>('thresholdDays')
const restriction = defineModel<RestrictionSelection>('restriction', {required: true})
const viewRestriction = defineModel<RestrictionSelection>('viewRestriction', {required: true})
const fields = defineModel<EventFieldEntry[]>('fields', {required: true})
const reminders = defineModel<number[]>('reminders', {required: true})

const {t} = useI18n()
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
        v-model:start-time="startTime" v-model:end-time="endTime"
        v-model:repeat-until="repeatUntil" v-model:repeat-count="repeatCount"
        v-model:requires-registration="requiresRegistration"
        v-model:requires-confirmation="requiresConfirmation"
        v-model:registration-deadline="registrationDeadline"
        v-model:registration-limit="registrationLimit"
        v-model:queue-order="queueOrder"
        v-model:min-registrations="minRegistrations"
        v-model:threshold-days="thresholdDays"
        v-model:restriction="restriction"
        v-model:view-restriction="viewRestriction"
        v-model:fields="fields"
        :categories="categories" :sign-up-held="signUpHeld"
        :templates="templates"
        :attendance-fields="attendanceFields"
        :groups="groups"
        :tags="tags"
        :all-members="allMembers"
        :group-members="groupMembers"
        :event-group-part="eventGroupPart"
        :follows-group="followsGroup"
        :attention="attention"
        :time-zone="timeZone"
        show-schedule
        show-value
    >
      <template #after-fields>
        <slot name="after-fields"/>
      </template>
      <template #sections>
        <FormSection
            v-if="!followsGroup?.reminders || reminders.length > 0"
            :title="t('formSections.reminders')"
            :summary="remindersSummary(reminders, t)"
            :attention="attention?.includes('reminders')"
            data-testid="form-section-reminders"
        >
          <EventReminderEditor v-model="reminders" compact/>
        </FormSection>
      </template>
    </EventFormPanel>
  </NeutralContainer>
</template>
