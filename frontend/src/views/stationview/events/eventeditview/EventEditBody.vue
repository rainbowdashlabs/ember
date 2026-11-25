/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref, watch, type ComponentPublicInstance} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import FormSection from '@/components/container/FormSection.vue'
import type {
    AppointmentField,
    AttendanceTemplate,
    AttendanceTemplateField,
    EventCategory,
    EventFieldEntry,
    EventGroupSettings,
    EventTemplate,
    MemberGroup,
    QueueOrder,
    PartnerResponse,
    RegistrationDeadline,
    RegistrationFieldDefinition,
    UserTag,
} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import type {RestrictionSelection} from '@/api/types'
import EventEditHeader from './EventEditHeader.vue'
import EventFormCard from './EventFormCard.vue'
import FederationCard from './FederationCard.vue'
import FieldDefaultsCard from './FieldDefaultsCard.vue'
import InheritedFieldsCard from './InheritedFieldsCard.vue'
import InheritedSettingsCard, {type InheritedSetting} from './InheritedSettingsCard.vue'
import PartLimitCard from './PartLimitCard.vue'
import {entryOf} from '../eventshared/eventQuestions'
import RegistrationFieldsEditor from '../eventshared/RegistrationFieldsEditor.vue'
import {useQuestionsRequireSignUp} from '../eventshared/useQuestionsRequireSignUp'
import type {FieldDefaultValue} from './useEventFieldDefaults'
import {documentsSummary, questionsSummary, sharingSummary} from '../eventshared/sectionSummaries'

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
  allMembers: MemberLike[]
  groupMembers: Map<number, MemberLike[]>
  currentTemplateFields: AttendanceTemplateField[]
  fieldDefaults: Map<number, FieldDefaultValue>
  partners: PartnerResponse[]
  /** What the share card says about an applied template's share it could not take over as it was. */
  federationNote?: string
  /** Whether the appointment is a part of an event group, which owns its sign-up, audiences and sharing. */
  eventGroupPart?: boolean
  /** The fields the appointment takes over from its event group. */
  inheritedFields?: AppointmentField[]
  /** The date settings the appointment takes over from its event group. */
  inheritedSettings?: EventGroupSettings | null
  /** The places per appointment the event group gives its parts, null for no limit. */
  inheritedPartLimit?: number | null
  /** How many documents to bring the appointment asks for, for the summary of their section. */
  documentCount?: number
  /** How many files the appointment hands over, or will once it is created, for the same summary. */
  fileCount?: number
  /** The folded sections that hold something the reader has to see now, such as after a refused save. */
  attention?: readonly string[]
  /** The station's time zone, which the deadline of the next date is worked out on. */
  stationTimezone?: string | null
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
const repeatUntil = defineModel<string>('repeatUntil', {required: true})
const repeatCount = defineModel<number | undefined>('repeatCount')
const requiresRegistration = defineModel<boolean>('requiresRegistration', {required: true})
const registrationFields = defineModel<RegistrationFieldDefinition[]>('registrationFields', {required: true})
const {signUpHeld} = useQuestionsRequireSignUp(() => registrationFields.value.length > 0, requiresRegistration)
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
const federationShared = defineModel<boolean>('federationShared', {required: true})
const federationScope = defineModel<string>('federationScope', {required: true})
const federationPartnerIds = defineModel<number[]>('federationPartnerIds', {required: true})
const federationPlaces = defineModel<Record<number, {decides: boolean; budget: number | null}>>(
    'federationPlaces', {required: true})

const {t} = useI18n()

const canSubmit = computed(() => !props.saving && !!name.value && !!startTime.value && !!endTime.value)

/**
 * The date settings a part takes from its event group, which the inherited settings card shows: the
 * form leaves out its own control for each until the part overrides it.
 */
const followsGroup = computed(() => {
  const inherited = props.eventGroupPart ? props.inheritedSettings : null
  return {
    category: inherited?.categoryId != null,
    template: inherited?.templateId != null,
    reminders: (inherited?.reminderDays.length ?? 0) > 0,
  }
})

/** The sections an override has just revealed a control in, which open so the control can be seen. */
const overridden = ref<string[]>([])

/** The sections that open by themselves: those the page asks for and those an override revealed. */
const attentionKeys = computed(() => [...(props.attention ?? []), ...overridden.value])

const sheet = ref<ComponentPublicInstance | null>(null)

/**
 * Hands the focus to the override button of a setting whose own control just disappeared, because its
 * own value was cleared and the event group's applies again. The focus would otherwise fall back to the
 * page and leave a keyboard reader at its top.
 */
function refocusOverride(setting: InheritedSetting) {
  void nextTick(() => {
    if (document.activeElement && document.activeElement !== document.body) return
    const root = sheet.value?.$el as HTMLElement | undefined
    root?.querySelector<HTMLElement>(`[data-testid="override-setting"][data-setting="${setting}"]`)?.focus()
  })
}

watch(categoryId, (now, before) => {
  if (followsGroup.value.category && before && !now) refocusOverride('category')
})
watch(templateId, (now, before) => {
  if (!before || now) return
  overridden.value = overridden.value.filter(key => key !== 'fields')
  if (followsGroup.value.template) refocusOverride('template')
})
watch(() => reminders.value.length, (now, before) => {
  if (before === 0 || now > 0) return
  overridden.value = overridden.value.filter(key => key !== 'reminders')
  if (followsGroup.value.reminders) refocusOverride('reminders')
})

/**
 * Copies a date setting of the event group into the appointment's own, which then replaces it here.
 * Taking over the attendance template takes its pre-fills along, since they belong to that template.
 * The section the own control appears in opens, so the override is visible where it can be changed.
 */
function overrideSetting(setting: InheritedSetting) {
  const inherited = props.inheritedSettings
  if (!inherited) return
  if (setting === 'template') overridden.value = [...overridden.value, 'fields']
  if (setting === 'reminders') overridden.value = [...overridden.value, 'reminders']
  if (setting === 'category' && inherited.categoryId != null) categoryId.value = String(inherited.categoryId)
  if (setting === 'reminders') reminders.value = [...inherited.reminderDays]
  if (setting === 'template' && inherited.templateId != null) {
    templateId.value = String(inherited.templateId)
    for (const entry of inherited.fieldDefaults) {
      emit('update:fieldDefaultSource', entry.fieldId, entry.source)
      emit('update:fieldDefaultValue', entry.fieldId, entry.value ?? '')
    }
  }
}

/** Whether the appointment takes another attendance sheet than its event group hands down. */
const takesOwnSheet = computed(() =>
    templateId.value !== '' && Number(templateId.value) !== (props.inheritedSettings?.templateId ?? null))

/** Copies a field of the event group into the appointment's own fields, which then replaces it here. */
function override(field: AppointmentField) {
  fields.value = [...fields.value, entryOf(field)]
}
</script>

<template>
  <NeutralContainer ref="sheet" sheet data-testid="event-form-sheet">
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
        v-model:start-time="startTime" v-model:end-time="endTime"
        v-model:repeat-until="repeatUntil" v-model:repeat-count="repeatCount"
        v-model:requires-registration="requiresRegistration"
        v-model:requires-confirmation="requiresConfirmation"
        v-model:registration-deadline="registrationDeadline"
        v-model:registration-limit="registrationLimit"
        v-model:queue-order="queueOrder"
        v-model:min-registrations="minRegistrations" v-model:threshold-days="thresholdDays"
        v-model:restriction="restriction" v-model:view-restriction="viewRestriction"
        v-model:fields="fields"
        v-model:reminders="reminders"
        :sign-up-held="signUpHeld"
        :categories="props.categories"
        :templates="props.templates"
        :attendance-fields="props.attendanceFields"
        :groups="props.groups"
        :tags="props.tags"
        :all-members="props.allMembers"
        :group-members="props.groupMembers"
        :event-group-part="props.eventGroupPart"
        :follows-group="followsGroup"
        :attention="attentionKeys"
        :time-zone="props.stationTimezone"
    >
      <template #after-fields>
        <InheritedFieldsCard
            v-if="props.eventGroupPart"
            :inherited="props.inheritedFields ?? []"
            :own="fields"
            :own-sheet="takesOwnSheet"
            @override="override"
        />
        <FieldDefaultsCard
            v-if="props.currentTemplateFields.length > 0"
            :fields="props.currentTemplateFields"
            :defaults="props.fieldDefaults"
            @update:source="(fieldId, source) => emit('update:fieldDefaultSource', fieldId, source)"
            @update:value="(fieldId, value) => emit('update:fieldDefaultValue', fieldId, value)"
        />
      </template>
    </EventFormCard>

    <PartLimitCard
        v-if="props.eventGroupPart"
        v-model="registrationLimit"
        :inherited="props.inheritedPartLimit ?? null"
    />

    <InheritedSettingsCard
        v-if="props.eventGroupPart && props.inheritedSettings"
        :settings="props.inheritedSettings"
        :categories="props.categories"
        :templates="props.templates"
        :own-category-id="categoryId"
        :own-template-id="templateId"
        :own-reminders="reminders"
        @override="overrideSetting"
    />

    <FormSection
        v-if="!props.eventGroupPart"
        :title="t('formSections.questions')"
        :summary="questionsSummary(registrationFields.length, t)"
        data-testid="form-section-questions"
    >
      <RegistrationFieldsEditor v-model="registrationFields" :all-members="props.allMembers" compact untitled/>
    </FormSection>

    <FormSection
        :title="t('formSections.documents')"
        :summary="documentsSummary(props.documentCount ?? 0, props.fileCount ?? 0, t)"
        data-testid="form-section-documents"
    >
      <slot/>
    </FormSection>

    <FormSection
        v-if="!props.eventGroupPart"
        :title="t('formSections.sharing')"
        :summary="sharingSummary(federationShared, federationScope, federationPartnerIds, t)"
        :attention="!!props.federationNote || attentionKeys.includes('sharing')"
        data-testid="form-section-sharing"
    >
      <FederationCard
          v-model:shared="federationShared"
          v-model:scope="federationScope"
          v-model:partner-ids="federationPartnerIds"
          v-model:places="federationPlaces"
          compact
          :partners="props.partners"
          :can-federate="props.canFederate"
          :note="props.federationNote"
      />
    </FormSection>

    <ButtonRow pair align="end" data-testid="event-form-actions">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="!canSubmit" @click="emit('submit')">
        {{ props.saving ? t('common.loading') : t('common.save') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
