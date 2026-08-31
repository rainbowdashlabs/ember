/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
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
import RepeatEndField from '@/views/stationview/events/eventshared/RepeatEndField.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventEdit.title')" :subtitle="t('helpCenter.eventEdit.subtitle')">
    <HelpSection :title="t('helpCenter.eventEdit.whatShown')">
      <p>{{ t('helpCenter.eventEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.loadTemplateTitle')">
      <p>{{ t('helpCenter.eventEdit.loadTemplateText') }}</p>
      <NeutralContainer class="mt-3">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <SectionHeader>{{ t('events.editEvent') }}</SectionHeader>
          <SelectInput model-value="" class="w-48 text-sm" disabled>
            <option value="" disabled>{{ t('eventTemplates.loadTemplate') }}</option>
            <option value="1">Standard-Übung</option>
          </SelectInput>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Form sections -->
    <HelpSection :title="t('helpCenter.eventEdit.generalTitle')">
      <p>{{ t('helpCenter.eventEdit.generalText') }}</p>
      <NeutralContainer class="space-y-4 mt-3">
        <SubHeader>{{ t('events.general') }}</SubHeader>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('events.name') }}</FieldLabel>
            <TextInput model-value="Übungsabend" disabled/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('events.type') }}</FieldLabel>
            <SelectInput model-value="RECURRING" disabled class="w-full">
              <option value="RECURRING">{{ t('events.typeRecurring') }}</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('events.startTime') }}</FieldLabel>
            <DateTimeInput model-value="2026-05-25T18:00" disabled/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('events.endTime') }}</FieldLabel>
            <DateTimeInput model-value="2026-05-25T20:00" disabled/>
          </div>
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
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('events.registrationDeadline') }}</FieldLabel>
            <DateTimeInput model-value="2026-05-24T18:00" disabled/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('events.registrationLimit') }}</FieldLabel>
            <NumberInput :model-value="15" disabled/>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.registrationFieldsTitle')">
      <p>{{ t('helpCenter.eventEdit.registrationFieldsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.registrationFieldsDefaults') }}</p>
      <p>{{ t('helpCenter.eventEdit.registrationFieldsOverview') }}</p>

      <!-- Dummy: a shirt size question -->
      <NeutralContainer class="space-y-3 mt-3">
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('events.registrationFields.fieldName') }}</FieldLabel>
            <TextInput model-value="Shirtgröße" disabled/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('events.registrationFields.defaultValue') }}</FieldLabel>
            <TextInput model-value="M" disabled/>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <ToggleInput :model-value="true"/>
          <span class="text-sm">{{ t('events.registrationFields.required') }}</span>
        </div>
        <div class="flex items-center gap-2">
          <ToggleInput :model-value="true"/>
          <span class="text-sm">{{ t('events.registrationFields.overview') }}</span>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.remindersTitle')">
      <p>{{ t('helpCenter.eventEdit.remindersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.restrictionsTitle')">
      <p>{{ t('helpCenter.eventEdit.restrictionsText') }}</p>
      <p>{{ t('helpCenter.eventEdit.restrictionsVisibilityText') }}</p>
      <p>{{ t('helpCenter.eventEdit.restrictionsLockText') }}</p>
      <p>{{ t('helpCenter.eventEdit.restrictionsOutsideText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.federationTitle')">
      <p>{{ t('helpCenter.eventEdit.federationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.fieldDefaultsTitle')">
      <p>{{ t('helpCenter.eventEdit.fieldDefaultsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.fieldsTitle')">
      <p>{{ t('helpCenter.eventEdit.fieldsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.fromAttendanceTitle')">
      <p>{{ t('helpCenter.eventEdit.fromAttendanceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventEdit.selfRegistrationTitle')">
      <p>{{ t('helpCenter.eventEdit.selfRegistrationText') }}</p>
      <HelpTip>{{ t('eventFields.selfRegistration') }}</HelpTip>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
