/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import EventFieldList from './EventFieldList.vue'
import RepeatEndField from './RepeatEndField.vue'
import type {AttendanceTemplate, AttendanceTemplateField} from '@/api/attendance'
import {EventTypes, needsDayOfWeek, type EventCategory, type EventFieldEntry} from '@/api/events'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'

defineProps<{
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  attendanceFields?: AttendanceTemplateField[]
  groups?: MemberGroup[]
  tags?: UserTag[]
  showSchedule?: boolean
  showValue?: boolean
  allMembers?: StationMember[]
  groupMembers?: Map<number, StationMember[]>
  tagMembers?: Map<number, StationMember[]>
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
const hasThreshold = defineModel<boolean>('hasThreshold')
const thresholdDate = defineModel<string>('thresholdDate')
const registrationCloseDays = defineModel<number>('registrationCloseDays')

const restriction = defineModel<RestrictionSelection>('restriction', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('events.general') }}</SubHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('events.name') }}</FieldLabel>
      <TextInput v-model="name" :placeholder="t('events.namePlaceholder')"/>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('events.description') }}</FieldLabel>
      <MarkdownEditor v-model="description" :placeholder="t('events.descriptionPlaceholder')"/>
    </div>

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

    <!-- Schedule section (only for single event creation) -->
    <template v-if="showSchedule && eventType !== undefined">
      <hr class="border-(--border)"/>
      <div class="space-y-2">
        <FieldLabel>{{ t('events.type') }}</FieldLabel>
        <ToggleSwitch
            :model-value="eventType === EventTypes.ONE_TIME ? 'onetime' : 'recurring'"
            option-a="onetime"
            option-b="recurring"
            :label-a="t('events.typeOneTime')"
            :label-b="t('events.typeRecurringShort')"
            data-testid="event-type-toggle"
            @update:model-value="eventType = $event === 'onetime' ? EventTypes.ONE_TIME : EventTypes.RECURRING"
        />
        <SelectInput v-if="eventType !== EventTypes.ONE_TIME" v-model="eventType" class="w-full">
          <option :value="EventTypes.RECURRING">{{ t('events.typeRecurring') }}</option>
          <option :value="EventTypes.MONTHLY_FIRST">{{ t('events.typeMonthlyFirst') }}</option>
          <option :value="EventTypes.QUARTERLY">{{ t('events.typeQuarterly') }}</option>
          <option :value="EventTypes.YEARLY">{{ t('events.typeYearly') }}</option>
        </SelectInput>
      </div>

      <div v-if="needsDayOfWeek(eventType)" class="space-y-1">
        <FieldLabel>{{ t('events.dayOfWeek') }}</FieldLabel>
        <SelectInput v-model="dayOfWeek" class="w-full">
          <option value="1">Montag</option>
          <option value="2">Dienstag</option>
          <option value="3">Mittwoch</option>
          <option value="4">Donnerstag</option>
          <option value="5">Freitag</option>
          <option value="6">Samstag</option>
          <option value="7">Sonntag</option>
        </SelectInput>
      </div>

      <RepeatEndField
          v-if="eventType !== EventTypes.ONE_TIME && repeatUntil !== undefined"
          v-model:until="repeatUntil"
          v-model:count="repeatCount"
      />

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('events.startTime') }}</FieldLabel>
          <DateTimeInput v-model="startTime"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('events.endTime') }}</FieldLabel>
          <DateTimeInput v-model="endTime"/>
        </div>
      </div>
    </template>

    <slot name="after-schedule" />

    <!-- Registration -->
    <template v-if="requiresRegistration !== undefined">
      <hr class="border-(--border)"/>
      <SubHeader>{{ t('events.registration') }}</SubHeader>

      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('events.requiresRegistration') }}</label>
        <ToggleInput v-model="requiresRegistration"/>
      </div>

      <template v-if="requiresRegistration">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</label>
          <ToggleInput v-model="requiresConfirmation"/>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('events.requiresConfirmationHint') }}</p>

        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('events.hasDeadline') }}</label>
          <ToggleInput v-model="hasDeadline"/>
        </div>

        <div v-if="hasDeadline" class="space-y-1">
          <FieldLabel>{{ t('events.registrationDeadline') }}</FieldLabel>
          <DateTimeInput v-model="registrationDeadline"/>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('events.registrationLimit') }}</FieldLabel>
          <NumberInput v-model="registrationLimit" :placeholder="t('events.registrationLimitHint')"/>
          <p class="text-xs text-(--text-muted)">{{ t('events.registrationLimitHint') }}</p>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('events.minRegistrations') }}</FieldLabel>
          <NumberInput v-model="minRegistrations" placeholder=""/>
        </div>

        <template v-if="minRegistrations && minRegistrations > 0">
          <div class="flex items-center justify-between">
            <label class="text-sm font-medium">{{ t('events.thresholdDate') }}</label>
            <ToggleInput v-model="hasThreshold"/>
          </div>
          <div v-if="hasThreshold" class="space-y-1">
            <FieldLabel>{{ t('events.thresholdDate') }}</FieldLabel>
            <DateTimeInput v-model="thresholdDate"/>
            <p class="text-xs text-(--text-muted)">{{ t('events.thresholdHint') }}</p>
          </div>
        </template>

        <div v-if="eventType !== undefined && eventType !== 'ONE_TIME'" class="space-y-1">
          <FieldLabel>{{ t('events.registrationCloseDays') }}</FieldLabel>
          <NumberInput v-model="registrationCloseDays" :placeholder="t('events.registrationCloseDaysHint')"/>
          <p class="text-xs text-(--text-muted)">{{ t('events.registrationCloseDaysHint') }}</p>
        </div>
      </template>
    </template>

    <!-- Restrictions -->
    <template v-if="groups && tags">
      <hr class="border-(--border)"/>
      <SubHeader>{{ t('events.restrictions') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('events.restrictToRolesHint') }}</p>
      <RestrictionsField
          :groups="groups"
          :tags="tags"
          v-model="restriction"
      />
    </template>

    <!-- Event Fields -->
    <hr class="border-(--border)"/>
    <EventFieldList
        v-model:fields="fields"
        :attendance-fields="attendanceFields"
        :show-value="showValue"
        :all-members="allMembers"
        :groups="groups"
        :group-members="groupMembers"
        :tags="tags"
        :tag-members="tagMembers"
    />
  </div>
</template>
