/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {AttendanceTemplate, AttendanceTemplateField, EventCategory, EventFieldEntry, EventTemplate, MemberGroup, StationMember, UserTag} from '@/api/types'
import type {PartnerResponse} from '@/api/federation'
import EventEditHeader from './EventEditHeader.vue'
import EventFormCard from './EventFormCard.vue'
import FederationCard from './FederationCard.vue'
import FieldDefaultsCard from './FieldDefaultsCard.vue'

interface FieldDefaultEntry {
  source: string
  value: string
}

const props = defineProps<{
  isEdit: boolean
  saving: boolean
  canFederate: boolean
  eventTemplates: EventTemplate[]
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  attendanceFields: AttendanceTemplateField[]
  groups: MemberGroup[]
  tags: UserTag[]
  allMembers: StationMember[]
  groupMembers: Map<number, StationMember[]>
  currentTemplateFields: AttendanceTemplateField[]
  fieldDefaults: Map<number, FieldDefaultEntry>
  partners: PartnerResponse[]
}>()

const emit = defineEmits<{
  'apply-template': [value: string | undefined]
  'update:fieldDefaultSource': [fieldId: number, source: string]
  'update:fieldDefaultValue': [fieldId: number, value: string]
  cancel: []
  submit: []
}>()

const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const templateId = defineModel<string>('templateId', {required: true})
const eventType = defineModel<string>('eventType', {required: true})
const dayOfWeek = defineModel<string>('dayOfWeek', {required: true})
const startTime = defineModel<string>('startTime', {required: true})
const endTime = defineModel<string>('endTime', {required: true})
const requiresRegistration = defineModel<boolean>('requiresRegistration', {required: true})
const requiresConfirmation = defineModel<boolean>('requiresConfirmation', {required: true})
const hasDeadline = defineModel<boolean>('hasDeadline', {required: true})
const registrationDeadline = defineModel<string>('registrationDeadline', {required: true})
const registrationLimit = defineModel<number | undefined>('registrationLimit')
const minRegistrations = defineModel<number | undefined>('minRegistrations')
const hasThreshold = defineModel<boolean>('hasThreshold', {required: true})
const thresholdDate = defineModel<string>('thresholdDate', {required: true})
const registrationCloseDays = defineModel<number | undefined>('registrationCloseDays')
const selectedUserTypes = defineModel<string[]>('selectedUserTypes', {required: true})
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', {required: true})
const selectedTagIds = defineModel<number[]>('selectedTagIds', {required: true})
const fields = defineModel<EventFieldEntry[]>('fields', {required: true})
const reminders = defineModel<number[]>('reminders', {required: true})
const federationShared = defineModel<boolean>('federationShared', {required: true})
const federationScope = defineModel<string>('federationScope', {required: true})
const federationPartnerIds = defineModel<number[]>('federationPartnerIds', {required: true})

const {t} = useI18n()

const canSubmit = computed(() => !props.saving && !!name.value && !!startTime.value && !!endTime.value)
</script>

<template>
  <EventEditHeader
      :is-edit="props.isEdit"
      :event-templates="props.eventTemplates"
      @apply-template="emit('apply-template', $event)"
  />

  <EventFormCard
      v-model:name="name"
      v-model:description="description"
      v-model:category-id="categoryId"
      v-model:template-id="templateId"
      v-model:event-type="eventType"
      v-model:day-of-week="dayOfWeek"
      v-model:start-time="startTime"
      v-model:end-time="endTime"
      v-model:requires-registration="requiresRegistration"
      v-model:requires-confirmation="requiresConfirmation"
      v-model:has-deadline="hasDeadline"
      v-model:registration-deadline="registrationDeadline"
      v-model:registration-limit="registrationLimit"
      v-model:min-registrations="minRegistrations"
      v-model:has-threshold="hasThreshold"
      v-model:threshold-date="thresholdDate"
      v-model:registration-close-days="registrationCloseDays"
      v-model:selected-user-types="selectedUserTypes"
      v-model:selected-group-ids="selectedGroupIds"
      v-model:selected-tag-ids="selectedTagIds"
      v-model:fields="fields"
      v-model:reminders="reminders"
      :categories="props.categories"
      :templates="props.templates"
      :attendance-fields="props.attendanceFields"
      :groups="props.groups"
      :tags="props.tags"
      :all-members="props.allMembers"
      :group-members="props.groupMembers"
  />

  <FederationCard
      v-model:shared="federationShared"
      v-model:scope="federationScope"
      v-model:partner-ids="federationPartnerIds"
      :partners="props.partners"
      :can-federate="props.canFederate"
  />

  <FieldDefaultsCard
      v-if="props.currentTemplateFields.length > 0"
      :fields="props.currentTemplateFields"
      :defaults="props.fieldDefaults"
      @update:source="(fieldId, source) => emit('update:fieldDefaultSource', fieldId, source)"
      @update:value="(fieldId, value) => emit('update:fieldDefaultValue', fieldId, value)"
  />

  <div class="flex justify-end gap-3">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <PrimaryButton :disabled="!canSubmit" @click="emit('submit')">
      {{ props.saving ? t('common.loading') : t('common.save') }}
    </PrimaryButton>
  </div>
</template>
