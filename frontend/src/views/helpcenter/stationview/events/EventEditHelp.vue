/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import {weekdayName} from '@/util/format'
import RepeatEndField from '@/views/stationview/events/eventshared/RepeatEndField.vue'
import RegistrationFieldsEditor from '@/views/stationview/events/eventshared/RegistrationFieldsEditor.vue'
import {emptySettings} from '@/views/stationview/events/eventshared/eventQuestions'
import InheritedSettingsCard from '@/views/stationview/events/eventeditview/InheritedSettingsCard.vue'
import QueueOrderField from '@/views/stationview/events/eventshared/QueueOrderField.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import type {AttendanceTemplate, EventCategory, EventGroupSettings, RegistrationFieldDefinition} from '@/api/generated/schema'

const {t} = useI18n()

/** A shirt size asked of everyone, in the editor that writes it. */
const SHIRT_SIZE: RegistrationFieldDefinition[] = [{
  name: 'Shirtgröße',
  fieldType: 'CHOICE',
  config: {...emptySettings(), options: ['S', 'M', 'L', 'XL'], required: true, defaultValue: 'M'},
  overview: true,
}]

/** What an example event group hands down: a category, an attendance sheet with one pre-fill and two reminders. */
const GROUP_SETTINGS: EventGroupSettings = {
  templateId: 4,
  categoryId: 1,
  reminderDays: [7, 1],
  fieldDefaults: [{eventId: 3, fieldId: 9, source: 'EVENT_NAME', value: null}],
}
const groupCategories = computed(() => [
  {id: 1, name: t('helpCenter.eventEdit.exampleGroupCategory')},
  {id: 2, name: t('helpCenter.eventEdit.examplePartCategory')},
] as EventCategory[])
const groupTemplates = computed(() => [{id: 4, name: t('helpCenter.eventEdit.exampleGroupTemplate')}] as AttendanceTemplate[])
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventEdit.title')" :subtitle="t('helpCenter.eventEdit.subtitle')">
    <HelpSection :title="t('helpCenter.eventEdit.whatShown')">
      <p>{{ t('helpCenter.eventEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.loadTemplateTitle')">
      <p>{{ t('helpCenter.eventEdit.loadTemplateText') }}</p>
      <p>{{ t('helpCenter.eventEdit.loadTemplateRulesText') }}</p>
      <p>{{ t('helpCenter.eventEdit.loadTemplateShareText') }}</p>
      <p>{{ t('helpCenter.eventEdit.loadTemplateFilesText') }}</p>
      <NeutralContainer class="mt-3">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <SectionHeader>{{ t('events.editEvent') }}</SectionHeader>
          <SelectInput model-value="" class="w-48 text-sm" disabled>
            <option value="" disabled>{{ t('eventTemplates.loadTemplate') }}</option>
            <option value="1">{{ t('helpCenter.sample.events.standardPractice') }}</option>
          </SelectInput>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.copyTitle')">
      <p>{{ t('helpCenter.eventEdit.copyText') }}</p>
      <p>{{ t('helpCenter.eventEdit.copyDatesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.generalTitle')">
      <p>{{ t('helpCenter.eventEdit.generalText') }}</p>
      <p>{{ t('helpCenter.eventEdit.generalFollowText') }}</p>
      <NeutralContainer class="space-y-4 mt-3">
        <TextInput model-value="Übungsabend" :aria-label="t('events.name')" class="text-lg font-semibold" disabled/>
        <div class="grid grid-cols-1 items-center gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:gap-3">
          <DateTimeInput model-value="2026-05-25T18:00" :label="t('events.startTime')" disabled/>
          <span class="text-sm text-(--text-muted)">{{ t('events.until') }}</span>
          <DateTimeInput model-value="2026-05-25T20:00" :label="t('events.endTime')" disabled/>
        </div>
        <div class="flex flex-wrap items-center gap-3">
          <label class="flex items-center gap-2 text-sm font-medium">
            <ToggleInput :model-value="true" disabled/>
            {{ t('events.typeRecurringShort') }}
          </label>
          <SelectInput model-value="RECURRING" :aria-label="t('events.type')" disabled>
            <option value="RECURRING">{{ t('events.typeRecurring') }}</option>
          </SelectInput>
          <SelectInput model-value="1" :aria-label="t('events.dayOfWeek')" disabled>
            <option value="1">{{ weekdayName(1) }}</option>
          </SelectInput>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.repeatEndTitle')">
      <p>{{ t('helpCenter.eventEdit.repeatEndText') }}</p>
      <NeutralContainer class="mt-3">
        <RepeatEndField :count="8" until=""/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.templateTitle')">
      <p>{{ t('helpCenter.eventEdit.templateText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.fieldsTitle')">
      <p>{{ t('helpCenter.eventEdit.fieldsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.fieldsLineText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.placeTitle')">
      <p>{{ t('helpCenter.eventEdit.placeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.fieldDefaultsTitle')">
      <p>{{ t('helpCenter.eventEdit.fieldDefaultsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.fromAttendanceTitle')">
      <p>{{ t('helpCenter.eventEdit.fromAttendanceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.selfRegistrationTitle')">
      <p>{{ t('helpCenter.eventEdit.selfRegistrationText') }}</p>
      <HelpTip>{{ t('eventFields.selfRegistration') }}</HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.remindersTitle')">
      <p>{{ t('helpCenter.eventEdit.remindersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.registrationTitle')">
      <p>{{ t('helpCenter.eventEdit.registrationText') }}</p>
      <NeutralContainer class="space-y-4 mt-3">
        <SubHeader>{{ t('events.registration') }}</SubHeader>
        <div class="flex items-center gap-2">
          <ToggleInput :model-value="true"/>
          <span class="text-sm">{{ t('events.requiresRegistration') }}</span>
        </div>
        <div class="flex items-center gap-2">
          <ToggleInput :model-value="false"/>
          <span class="text-sm">{{ t('events.requiresConfirmation') }}</span>
          <HintIcon :text="t('events.requiresConfirmationHint')"/>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('events.registrationDeadline') }}</FieldLabel>
            <DateTimeInput model-value="2026-05-24T18:00" disabled/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('events.registrationLimit') }}</FieldLabel>
            <NumberInput :model-value="15" disabled/>
            <FieldHint>{{ t('registrationQueue.limitHint') }}</FieldHint>
          </div>
        </div>
        <QueueOrderField model-value="TIME"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.queueTitle')">
      <p>{{ t('helpCenter.eventEdit.queueText') }}</p>
      <p>{{ t('helpCenter.eventEdit.queueOrderText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.minimumTitle')">
      <p>{{ t('helpCenter.eventEdit.minimumText') }}</p>
      <p>{{ t('helpCenter.eventEdit.minimumSeriesText') }}</p>
      <NeutralContainer class="mt-3">
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('events.minRegistrations') }}</FieldLabel>
            <NumberInput :model-value="6" disabled/>
          </div>
          <div class="space-y-1">
            <div class="flex items-center gap-1">
              <FieldLabel>{{ t('events.thresholdDays') }}</FieldLabel>
              <HintIcon :text="t('events.thresholdDaysHint')"/>
            </div>
            <NumberInput :model-value="3" disabled/>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.restrictionsTitle')">
      <p>{{ t('helpCenter.eventEdit.restrictionsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.restrictionsButtonsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.restrictionsVisibilityText') }}</p>
      <p>{{ t('helpCenter.eventEdit.restrictionsLockText') }}</p>
      <p>{{ t('helpCenter.eventEdit.restrictionsOutsideText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.registrationFieldsTitle')">
      <p>{{ t('helpCenter.eventEdit.registrationFieldsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.registrationFieldsDefaults') }}</p>
      <p>{{ t('helpCenter.eventEdit.registrationFieldsOverview') }}</p>
      <p>{{ t('helpCenter.eventEdit.registrationFieldsDescription') }}</p>

      <RegistrationFieldsEditor :model-value="SHIRT_SIZE" class="mt-3"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.documentsTitle')">
      <p>{{ t('helpCenter.eventEdit.documentsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.documentsSigningText') }}</p>
      <p>{{ t('helpCenter.eventEdit.documentsTemplateText') }}</p>
      <p>{{ t('helpCenter.eventEdit.documentsPartnersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.attachmentsTitle')">
      <p>{{ t('helpCenter.eventEdit.attachmentsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.attachmentsSavedText') }}</p>
      <p>{{ t('helpCenter.eventEdit.attachmentsInternalText') }}</p>
      <p>{{ t('helpCenter.eventEdit.attachmentsFilesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.federationTitle')">
      <p>{{ t('helpCenter.eventEdit.federationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.saveTitle')">
      <p>{{ t('helpCenter.eventEdit.saveText') }}</p>
      <p>{{ t('helpCenter.eventEdit.saveScopeText') }}</p>
      <p>{{ t('helpCenter.eventEdit.saveNoticeText') }}</p>
      <p>{{ t('helpCenter.eventEdit.saveMovedText') }}</p>
      <HelpTip>{{ t('helpCenter.eventEdit.saveTip') }}</HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.inheritedSettingsTitle')">
      <p>{{ t('helpCenter.eventEdit.inheritedSettingsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.inheritedFilesText') }}</p>
      <InheritedSettingsCard
          class="mt-3"
          :settings="GROUP_SETTINGS"
          :categories="groupCategories"
          :templates="groupTemplates"
          own-category-id="2"
          own-template-id=""
          :own-reminders="[]"
      />
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventEdit.eventGroupPart') }}</HelpTip>
    <HelpTip>{{ t('helpCenter.eventEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
