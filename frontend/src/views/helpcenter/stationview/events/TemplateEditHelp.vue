/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {
  FieldType,
  ShareScope,
  type AttendanceTemplateField,
  type EventAttachment,
  type EventCategory,
  type EventFieldEntry,
} from '@/api/generated/schema'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EventDefaultsSection from '@/views/stationview/events/templateeditview/EventDefaultsSection.vue'
import EventReminderEditor from '@/views/stationview/events/eventshared/EventReminderEditor.vue'
import EventFieldList from '@/views/stationview/events/eventshared/EventFieldList.vue'
import TemplateSignUpSection from '@/views/stationview/events/templateeditview/TemplateSignUpSection.vue'
import FederationCard from '@/views/stationview/events/eventeditview/FederationCard.vue'
import FieldDefaultsCard from '@/views/stationview/events/eventeditview/FieldDefaultsCard.vue'
import AttachmentsCard from '@/views/stationview/events/eventeditview/AttachmentsCard.vue'

const {t} = useI18n()

const CATEGORIES: EventCategory[] = [
  {id: 1, stationId: 'demo', name: 'Übung', position: 0, maxShownEvents: null, isPublic: false, color: null},
]
const ATTENDANCE_TEMPLATES = [{id: 1, stationId: 'demo', name: 'Übungsabend'}]
const FIELDS: EventFieldEntry[] = [
  {name: 'Ort', fieldType: 'TEXT', overview: true, isPublic: true, value: 'Gerätehaus'},
  {name: 'Treffpunkt', fieldType: 'TEXT', overview: true, isPublic: false, value: 'Fahrzeughalle'},
]
const REMINDERS = [7, 1]

/** Two fields of the chosen attendance sheet, one filled from the appointment's name. */
const SHEET_FIELDS: AttendanceTemplateField[] = [
  {
    id: 1, templateId: 1, position: 0, name: 'Thema', fieldType: FieldType.TEXT,
    config: {autoAttend: false, defaultValue: null, groupId: null, options: null, required: false, width: null},
  },
  {
    id: 2, templateId: 1, position: 1, name: 'Ausbildungsort', fieldType: FieldType.TEXT,
    config: {autoAttend: false, defaultValue: null, groupId: null, options: null, required: false, width: null},
  },
]
const FIELD_DEFAULTS = new Map([
  [1, {source: 'EVENT_NAME', value: ''}],
  [2, {source: 'VALUE', value: 'Übungsgelände'}],
])

/** The run sheet every appointment made from the template carries. */
const FILES: EventAttachment[] = [{
  id: 1, eventId: 1, fileId: 1, label: 'Laufzettel', internal: false, sortOrder: 0,
  createdAt: '2026-05-12T10:00:00Z', fileName: 'laufzettel.pdf', mimeType: 'application/pdf',
  fileSize: 84_000, contentHash: 'demo',
}]

const noop = () => undefined
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventTemplateEdit.title')" :subtitle="t('helpCenter.eventTemplateEdit.subtitle')">
    <HelpSection :title="t('helpCenter.eventTemplateEdit.whatIs')">
      <p>{{ t('helpCenter.eventTemplateEdit.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.howTo')">
      <p>{{ t('helpCenter.eventTemplateEdit.howToStep1') }}</p>
      <p>{{ t('helpCenter.eventTemplateEdit.howToStep2') }}</p>
      <p>{{ t('helpCenter.eventTemplateEdit.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.exampleTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.exampleText') }}</p>
      <div class="space-y-6">
        <div class="flex items-center gap-2">
          <SecondaryButton :icon="['fas', 'arrow-left']"/>
          <SectionHeader>{{ t('eventTemplates.edit') }}</SectionHeader>
        </div>
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('eventTemplates.name') }}</SubHeader>
          <TextInput model-value="Standard-Übung" :placeholder="t('eventTemplates.namePlaceholder')"/>
        </NeutralContainer>
        <EventDefaultsSection title="Übungsabend" description="Wöchentliche Übung der Einsatzabteilung."
                              category-id="1" event-type="RECURRING" attendance-template-id="1"
                              :categories="CATEGORIES" :attendance-templates="ATTENDANCE_TEMPLATES"/>
        <TemplateSignUpSection event-type="RECURRING" :requires-registration="true" :requires-confirmation="false"
                               :registration-limit="30" :deadline-offset-hours="26" :min-registrations="6"
                               :threshold-days="3" :registration-close-days="1"/>
        <NeutralContainer>
          <EventReminderEditor :model-value="REMINDERS"/>
        </NeutralContainer>
        <NeutralContainer class="space-y-4">
          <EventFieldList :fields="FIELDS" :value-label="t('eventFields.defaultValue')" show-value/>
        </NeutralContainer>
        <FederationCard :shared="true" :scope="ShareScope.ALL_PARTNERS" :partner-ids="[]" :partners="[]"
                        can-federate :hint="t('eventTemplates.federationHint')"/>
        <FieldDefaultsCard :fields="SHEET_FIELDS" :defaults="FIELD_DEFAULTS"/>
        <AttachmentsCard :attachments="FILES" station-uid="demo" :hint="t('eventTemplates.attachmentsHint')"/>
        <SaveButton :action="noop"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.defaultsTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.defaultsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.signUpTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.signUpText') }}</p>
      <p>{{ t('helpCenter.eventTemplateEdit.signUpDeadlineText') }}</p>
      <p>{{ t('helpCenter.eventTemplateEdit.signUpRulesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.remindersTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.remindersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.fieldsTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.fieldsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.fromAttendanceTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.fromAttendanceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.fieldValueTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.fieldValueText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.fieldOrderTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.fieldOrderText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.shareTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.shareText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.fieldDefaultsTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.fieldDefaultsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.filesTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.filesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.documentsTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.documentsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.notCarriedTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.notCarriedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.backTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.backText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventTemplateEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
