/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useId, watch} from 'vue'
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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {emptyRestriction, namesSomebody} from '@/components/input/restriction'
import RestrictionRow from './RestrictionRow.vue'
import type {RestrictionSelection} from '@/api/types'
import EventFieldList from './EventFieldList.vue'
import RepeatEndField from './RepeatEndField.vue'
import {isRecurringEvent, needsDayOfWeek} from '@/api/events'
import {
    EventType,
    type AttendanceTemplate,
    type AttendanceTemplateField,
    type EventCategory,
    type EventFieldEntry,
    type MemberGroup,
    type UserTag,
} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import {weekdayName} from '@/util/format'

defineProps<{
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
const hasDeadline = defineModel<boolean>('hasDeadline')
const registrationDeadline = defineModel<string>('registrationDeadline')
const registrationLimit = defineModel<number>('registrationLimit')
const minRegistrations = defineModel<number>('minRegistrations')
const thresholdDays = defineModel<number>('thresholdDays')
const registrationCloseDays = defineModel<number>('registrationCloseDays')

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
 * Whether a restriction shows its picker. It does once its button was pressed, and on its own as soon
 * as it names somebody, so an appointment loaded or filled from a template shows what limits it.
 * Once shown it stays until it is taken away, also while its selection is emptied to pick another.
 */
const restrictsRegistration = ref(false)
const restrictsVisibility = ref(false)

watch(() => namesSomebody(restriction.value), named => {
  if (named) restrictsRegistration.value = true
}, {immediate: true})
watch(() => namesSomebody(viewRestriction.value), named => {
  if (named) restrictsVisibility.value = true
}, {immediate: true})

/** Takes a restriction away again, which opens the appointment to everybody on that count. */
function liftRestriction(which: 'registration' | 'visibility') {
  if (which === 'registration') {
    restrictsRegistration.value = false
    restriction.value = emptyRestriction()
  } else {
    restrictsVisibility.value = false
    viewRestriction.value = emptyRestriction()
  }
}

const {t} = useI18n()

const labelId = useId()
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

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('events.category') }}</FieldLabel>
        <SelectInput v-model="categoryId" class="w-full">
          <option value="">-</option>
          <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('events.template') }}</FieldLabel>
        <SelectInput v-model="templateId" class="w-full" data-testid="event-attendance-template">
          <option value="">-</option>
          <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
        </SelectInput>
      </div>
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

    <div class="space-y-1">
      <FieldLabel>{{ t('events.description') }}</FieldLabel>
      <MarkdownEditor v-model="description" :placeholder="t('events.descriptionPlaceholder')"/>
    </div>

    <slot name="after-description"/>

    <template v-if="requiresRegistration !== undefined && !eventGroupPart">
      <hr class="border-(--border)"/>
      <SubHeader>{{ t('events.registration') }}</SubHeader>

      <div class="flex items-center justify-between">
        <span :id="`${labelId}-registration`" class="text-sm font-medium">{{ t('events.requiresRegistration') }}</span>
        <ToggleInput v-model="requiresRegistration" :aria-labelledby="`${labelId}-registration`"/>
      </div>

      <template v-if="requiresRegistration">
        <div class="flex items-center justify-between">
          <span class="flex items-center gap-1">
            <span :id="`${labelId}-confirmation`" class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</span>
            <HintIcon :text="t('events.requiresConfirmationHint')"/>
          </span>
          <ToggleInput v-model="requiresConfirmation" :aria-labelledby="`${labelId}-confirmation`"/>
        </div>

        <div class="flex items-center justify-between">
          <span :id="`${labelId}-deadline`" class="text-sm font-medium">{{ t('events.hasDeadline') }}</span>
          <ToggleInput v-model="hasDeadline" :aria-labelledby="`${labelId}-deadline`"/>
        </div>

        <div v-if="hasDeadline" class="space-y-1">
          <FieldLabel>{{ t('events.registrationDeadline') }}</FieldLabel>
          <DateTimeInput v-model="registrationDeadline"/>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('events.registrationLimit') }}</FieldLabel>
          <NumberInput v-model="registrationLimit" :placeholder="t('events.registrationLimitHint')"/>
        </div>

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

        <div v-if="eventType !== undefined && eventType !== 'ONE_TIME'" class="space-y-1">
          <div class="flex items-center gap-1">
            <FieldLabel>{{ t('events.registrationCloseDays') }}</FieldLabel>
            <HintIcon :text="t('events.registrationCloseDaysHint')"/>
          </div>
          <NumberInput v-model="registrationCloseDays"/>
        </div>
      </template>
    </template>

    <template v-if="groups && tags && !eventGroupPart">
      <hr class="border-(--border)"/>
      <SubHeader>{{ t('events.restrictions') }}</SubHeader>

      <RestrictionRow
          v-if="restrictsRegistration"
          v-model="restriction"
          :label="t('events.restrictedRegistration')"
          :hint="t('events.restrictToRolesHint')"
          :groups="groups"
          :tags="tags"
          data-testid="restriction-registration"
          @remove="liftRestriction('registration')"
      />
      <RestrictionRow
          v-if="restrictsVisibility"
          v-model="viewRestriction"
          :label="t('events.restrictedVisibility')"
          :hint="t('events.restrictVisibilityHint')"
          :groups="groups"
          :tags="tags"
          data-testid="restriction-visibility"
          @remove="liftRestriction('visibility')"
      />
      <ButtonRow v-if="!restrictsRegistration || !restrictsVisibility">
        <SecondaryButton v-if="!restrictsRegistration" :icon="['fas', 'plus']" data-testid="restrict-registration"
                         @click="restrictsRegistration = true">
          {{ t('events.restrictToRoles') }}
        </SecondaryButton>
        <SecondaryButton v-if="!restrictsVisibility" :icon="['fas', 'plus']" data-testid="restrict-visibility"
                         @click="restrictsVisibility = true">
          {{ t('events.restrictVisibility') }}
        </SecondaryButton>
      </ButtonRow>
    </template>
  </div>
</template>
