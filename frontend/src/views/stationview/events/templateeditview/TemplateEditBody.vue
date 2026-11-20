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
import FormSection from '@/components/container/FormSection.vue'
import EventFieldList from '../eventshared/EventFieldList.vue'
import RegistrationFieldsEditor from '../eventshared/RegistrationFieldsEditor.vue'
import EventReminderEditor from '../eventshared/EventReminderEditor.vue'
import EventDefaultsSection from './EventDefaultsSection.vue'
import TemplateAudienceSection from './TemplateAudienceSection.vue'
import TemplateSignUpSection from './TemplateSignUpSection.vue'
import type {RestrictionSelection} from '@/api/types'
import {isRecurringEvent} from '@/api/events'
import {useQuestionsRequireSignUp} from '../eventshared/useQuestionsRequireSignUp'
import {audienceSummary, fieldsSummary, questionsSummary, remindersSummary} from '../eventshared/sectionSummaries'
import type {
    AttendanceTemplate,
    AttendanceTemplateField,
    EventCategory,
    EventFieldEntry,
    MemberGroup,
    QueueOrder,
    RegistrationDeadline,
    RegistrationFieldDefinition,
    UserTag,
} from '@/api/generated/schema'

/**
 * Everything a template is edited through, once it has been loaded.
 *
 * <p>Its own component because the view around it is the loading, the error and the way back, and
 * the template itself is six sections that have nothing to do with any of those. What the view adds
 * of its own, such as the documents to bring, stands in the slot above the save button. As in the
 * appointment form, the name, the defaults and the sign-up stand open, and who may sign up, the
 * reminders, the fields and the questions are folded sections that say what they hold.
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
const queueOrder = defineModel<QueueOrder>('queueOrder', {required: true})
const registrationDeadline = defineModel<RegistrationDeadline>('registrationDeadline', {required: true})
const minRegistrations = defineModel<number | undefined>('minRegistrations')
const thresholdDays = defineModel<number | undefined>('thresholdDays')
const restriction = defineModel<RestrictionSelection>('restriction', {required: true})
const viewRestriction = defineModel<RestrictionSelection>('viewRestriction', {required: true})
const reminderDays = defineModel<number[]>('reminderDays', {required: true})
const fields = defineModel<EventFieldEntry[]>('fields', {required: true})
const registrationFields = defineModel<RegistrationFieldDefinition[]>('registrationFields', {required: true})
const {signUpHeld} = useQuestionsRequireSignUp(() => registrationFields.value.length > 0, requiresRegistration)

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
      :categories="categories"
      :attendance-templates="attendanceTemplates"
  />

  <TemplateSignUpSection
      v-model:requires-registration="requiresRegistration"
      v-model:requires-confirmation="requiresConfirmation"
      v-model:registration-limit="registrationLimit"
      v-model:queue-order="queueOrder"
      v-model:registration-deadline="registrationDeadline"
      v-model:min-registrations="minRegistrations"
      v-model:threshold-days="thresholdDays"
      :sign-up-held="signUpHeld"
  />

  <NeutralContainer sheet>
    <FormSection
        :title="t('formSections.audience')"
        :summary="audienceSummary(restriction, viewRestriction, t)"
        data-testid="form-section-audience"
    >
      <TemplateAudienceSection
          v-model:restriction="restriction"
          v-model:view-restriction="viewRestriction"
          :groups="groups"
          :tags="tags"
      />
    </FormSection>
  </NeutralContainer>

  <NeutralContainer>
    <FormSection
        :title="t('formSections.reminders')"
        :summary="remindersSummary(reminderDays, t)"
        data-testid="form-section-reminders"
    >
      <EventReminderEditor v-model="reminderDays" compact/>
    </FormSection>
  </NeutralContainer>

  <NeutralContainer>
    <FormSection
        :title="t('formSections.fieldsOnly')"
        :summary="fieldsSummary('', fields.length, t)"
        data-testid="form-section-fields"
    >
      <EventFieldList
          v-model:fields="fields"
          :attendance-fields="sheetFields"
          :groups="groups"
          :tags="tags"
          :recurring="isRecurringEvent(eventType)"
          :value-label="t('eventFields.defaultValue')"
          show-value
      />
    </FormSection>
  </NeutralContainer>

  <NeutralContainer sheet>
    <FormSection
        :title="t('formSections.questions')"
        :summary="questionsSummary(registrationFields.length, t)"
        data-testid="form-section-questions"
    >
      <RegistrationFieldsEditor v-model="registrationFields" untitled/>
    </FormSection>
  </NeutralContainer>

  <slot/>

  <SaveButton :disabled="!name.trim()" :action="save"/>
</template>
