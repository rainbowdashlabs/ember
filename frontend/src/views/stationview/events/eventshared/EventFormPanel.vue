/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import FormSection from '@/components/container/FormSection.vue'
import {audienceSummary, fieldsSummary, turnoutSummary} from './sectionSummaries'
import AudienceRestrictions from './AudienceRestrictions.vue'
import type {RestrictionSelection} from '@/api/types'
import EventFieldList from './EventFieldList.vue'
import RepeatEndField from './RepeatEndField.vue'
import QueueOrderField from './QueueOrderField.vue'
import {isRecurringEvent, needsDayOfWeek} from '@/api/events'
import {
    EventType,
    type QueueOrder,
    type AttendanceTemplate,
    type AttendanceTemplateField,
    type EventCategory,
    type EventFieldEntry,
    type MemberGroup,
    type RegistrationDeadline,
    type UserTag,
} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import {stationToday, weekdayName} from '@/util/format'
import RegistrationDeadlineInput from './RegistrationDeadlineInput.vue'
import {nextSeriesDay, placedOn} from '../eventeditview/copyDates'

const props = defineProps<{
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  attendanceFields?: AttendanceTemplateField[]
  groups?: MemberGroup[]
  tags?: UserTag[]
  showSchedule?: boolean
  showValue?: boolean
  allMembers?: MemberLike[]
  groupMembers?: Map<number, MemberLike[]>
  tagMembers?: Map<number, MemberLike[]>
  /**
   * Whether the appointment is a part of an event group. The event group decides its sign-up and its
   * audiences, and it stays a one-time appointment, so those settings are left out here.
   */
  eventGroupPart?: boolean
  /** Whether questions are asked, which are answered when signing up, so sign-up stays on. */
  signUpHeld?: boolean
  /**
   * The settings the event group hands down to a part, where it does: the inherited settings card
   * shows them, so the form leaves out its own control until the part overrides the setting.
   */
  followsGroup?: {category: boolean; template: boolean}
  /** Whether the fields stand open from the start, where defining them is what the page is for. */
  openFields?: boolean
  /** The folded sections that hold something the reader has to see now, by key, which opens them. */
  attention?: readonly string[]
  /** The station's time zone, which the deadline of the next date is worked out on. */
  timeZone?: string | null
}>()

const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const templateId = defineModel<string>('templateId', {required: true})
const fields = defineModel<EventFieldEntry[]>('fields', {required: true})

const eventType = defineModel<string>('eventType')
const dayOfWeek = defineModel<string>('dayOfWeek')
const startTime = defineModel<string>('startTime')
const endTime = defineModel<string>('endTime')
const repeatUntil = defineModel<string>('repeatUntil')
const repeatCount = defineModel<number>('repeatCount')

const requiresRegistration = defineModel<boolean>('requiresRegistration')
const requiresConfirmation = defineModel<boolean>('requiresConfirmation')
const registrationDeadline = defineModel<RegistrationDeadline>('registrationDeadline')
const registrationLimit = defineModel<number>('registrationLimit')
const queueOrder = defineModel<QueueOrder>('queueOrder')
const minRegistrations = defineModel<number>('minRegistrations')
const thresholdDays = defineModel<number>('thresholdDays')

const restriction = defineModel<RestrictionSelection>('restriction', {required: true})
const viewRestriction = defineModel<RestrictionSelection>('viewRestriction', {required: true})

/**
 * Whether the appointment finishes before it begins.
 *
 * <p>An appointment may run past midnight and over several days, so only the one order that cannot
 * happen is called out. It reaches further than the appointment itself: an attendance sheet takes
 * its times from here.
 */
const endsBeforeItStarts = computed(() =>
    !!startTime.value && !!endTime.value && new Date(endTime.value) < new Date(startTime.value))

/** Whether the appointment repeats, in any rhythm. */
const recurring = computed(() => eventType.value !== undefined && eventType.value !== EventType.ONE_TIME)

/** Switches between a single appointment and a weekly series, the rhythm a series starts with. */
function setRecurring(repeats: boolean) {
  eventType.value = repeats ? EventType.RECURRING : EventType.ONE_TIME
}

/**
 * The start of the date the deadline preview speaks of: the start entered, or for a series whose
 * start has passed the next day its pattern falls on, at the same time of day.
 */
const nextStart = computed(() => {
  const start = startTime.value
  if (!start || !recurring.value || !eventType.value) return start || null
  const today = stationToday(props.timeZone)
  const firstDay = start.slice(0, 10)
  if (firstDay >= today) return start
  const weekday = needsDayOfWeek(eventType.value) ? Number(dayOfWeek.value) : null
  return placedOn(start, '', nextSeriesDay(eventType.value, weekday, firstDay, today)).startTime
})

const {t} = useI18n()

const labelId = useId()

const fieldsOpen = ref(props.openFields ?? false)

/** Whether the category is chosen here, which a part leaves to its event group until it overrides it. */
const showsCategory = computed(() => !props.followsGroup?.category || categoryId.value !== '')

/** Whether the attendance template is chosen here, on the same terms as the category. */
const showsTemplate = computed(() => !props.followsGroup?.template || templateId.value !== '')

/** The name of the attendance template chosen here, empty for none. */
const sheetName = computed(() =>
    props.templates.find(template => String(template.id) === templateId.value)?.name ?? '')
</script>

<template>
  <div class="space-y-4">
    <TextInput v-model="name" :aria-label="t('events.name')" :placeholder="t('events.namePlaceholder')"
               class="text-lg font-semibold"/>

    <template v-if="showSchedule && eventType !== undefined">
      <div class="space-y-1">
        <div class="grid grid-cols-1 items-center gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:gap-3">
          <DateTimeInput v-model="startTime" :label="t('events.startTime')" data-testid="event-start"/>
          <span class="text-sm text-(--text-muted)">{{ t('events.until') }}</span>
          <DateTimeInput v-model="endTime" :label="t('events.endTime')" :min="startTime" data-testid="event-end"/>
        </div>
        <FieldHint v-if="endsBeforeItStarts" data-testid="event-end-before-start" class="text-error">
          {{ t('events.endBeforeStart') }}
        </FieldHint>
      </div>

      <div v-if="!eventGroupPart" class="flex flex-wrap items-center gap-3">
        <label class="flex items-center gap-2 text-sm font-medium" data-testid="event-type-toggle">
          <ToggleInput :model-value="recurring" @update:model-value="setRecurring"/>
          {{ t('events.typeRecurringShort') }}
        </label>
        <SelectInput v-if="recurring" v-model="eventType" :aria-label="t('events.type')">
          <option :value="EventType.RECURRING">{{ t('events.typeRecurring') }}</option>
          <option :value="EventType.MONTHLY_FIRST">{{ t('events.typeMonthlyFirst') }}</option>
          <option :value="EventType.QUARTERLY">{{ t('events.typeQuarterly') }}</option>
          <option :value="EventType.YEARLY">{{ t('events.typeYearly') }}</option>
        </SelectInput>
        <SelectInput v-if="needsDayOfWeek(eventType)" v-model="dayOfWeek" :aria-label="t('events.dayOfWeek')">
          <option v-for="day in 7" :key="day" :value="String(day)">{{ weekdayName(day) }}</option>
        </SelectInput>
      </div>

      <RepeatEndField
          v-if="recurring && repeatUntil !== undefined"
          v-model:until="repeatUntil"
          v-model:count="repeatCount"
      />
    </template>

    <div v-if="showsCategory" class="space-y-1">
      <FieldLabel>{{ t('events.category') }}</FieldLabel>
      <SelectInput v-model="categoryId" class="w-full">
        <option value="">-</option>
        <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
      </SelectInput>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('events.description') }}</FieldLabel>
      <MarkdownEditor v-model="description" :placeholder="t('events.descriptionPlaceholder')"/>
    </div>

    <template v-if="requiresRegistration !== undefined && !eventGroupPart">
      <hr class="border-(--border)"/>
      <SubHeader>{{ t('events.registration') }}</SubHeader>

      <div class="flex items-center justify-between">
        <span class="flex items-center gap-1">
          <span :id="`${labelId}-registration`" class="text-sm font-medium">{{ t('events.requiresRegistration') }}</span>
          <HintIcon v-if="signUpHeld" :text="t('events.signUpHeldByQuestions')"/>
        </span>
        <ToggleInput v-model="requiresRegistration" :disabled="signUpHeld" :aria-labelledby="`${labelId}-registration`"/>
      </div>

      <template v-if="requiresRegistration">
        <div class="flex items-center justify-between">
          <span class="flex items-center gap-1">
            <span :id="`${labelId}-confirmation`" class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</span>
            <HintIcon :text="t('events.requiresConfirmationHint')"/>
          </span>
          <ToggleInput v-model="requiresConfirmation" :aria-labelledby="`${labelId}-confirmation`"/>
        </div>

        <RegistrationDeadlineInput
            v-if="registrationDeadline"
            v-model="registrationDeadline"
            :time-zone="timeZone"
            :next-start="nextStart"
            :recurring="recurring"
        />

        <div class="space-y-1">
          <FieldLabel>{{ t('events.registrationLimit') }}</FieldLabel>
          <NumberInput v-model="registrationLimit" :placeholder="t('events.registrationLimitHint')"/>
          <FieldHint>{{ t('registrationQueue.limitHint') }}</FieldHint>
        </div>

        <QueueOrderField v-if="queueOrder !== undefined && registrationLimit" v-model="queueOrder"/>
      </template>
    </template>

    <FormSection
        v-if="requiresRegistration !== undefined && !eventGroupPart && requiresRegistration"
        :title="t('formSections.turnoutMinimum')"
        :summary="turnoutSummary(minRegistrations, thresholdDays, t)"
        data-testid="form-section-turnout"
    >
      <div class="space-y-1">
        <FieldLabel>{{ t('events.minRegistrations') }}</FieldLabel>
        <NumberInput v-model="minRegistrations" placeholder=""/>
      </div>

      <div v-if="minRegistrations && minRegistrations > 0" class="space-y-1">
        <div class="flex items-center gap-1">
          <FieldLabel>{{ t('events.thresholdDays') }}</FieldLabel>
          <HintIcon :text="t('events.thresholdDaysHint')"/>
        </div>
        <NumberInput v-model="thresholdDays" :placeholder="t('events.thresholdDaysPlaceholder')"/>
      </div>
    </FormSection>

    <FormSection
        v-model:open="fieldsOpen"
        :title="t('formSections.fields')"
        :summary="fieldsSummary(sheetName, fields.length, t, !!followsGroup?.template)"
        :attention="attention?.includes('fields')"
        data-testid="form-section-fields"
    >
      <div v-if="showsTemplate" class="space-y-1">
        <FieldLabel>{{ t('events.template') }}</FieldLabel>
        <SelectInput v-model="templateId" class="w-full" data-testid="event-attendance-template">
          <option value="">-</option>
          <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
        </SelectInput>
      </div>

      <EventFieldList
          v-model:fields="fields"
          compact
          :attendance-fields="attendanceFields"
          :show-value="showValue"
          :all-members="allMembers"
          :groups="groups"
          :group-members="groupMembers"
          :tags="tags"
          :tag-members="tagMembers"
          :recurring="isRecurringEvent(eventType)"
      />

      <slot name="after-fields"/>
    </FormSection>

    <slot name="sections"/>

    <FormSection
        v-if="groups && tags && !eventGroupPart"
        :title="t('formSections.audience')"
        :summary="audienceSummary(restriction, viewRestriction, t)"
        :attention="attention?.includes('audience')"
        data-testid="form-section-audience"
    >
      <AudienceRestrictions v-model:registration="restriction" v-model:visibility="viewRestriction" :groups="groups" :tags="tags"/>
    </FormSection>
  </div>
</template>
