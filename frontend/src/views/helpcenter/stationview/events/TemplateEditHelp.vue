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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EventDefaultsSection from '@/views/stationview/events/templateeditview/EventDefaultsSection.vue'
import EventReminderEditor from '@/views/stationview/events/eventshared/EventReminderEditor.vue'
import EventFieldList from '@/views/stationview/events/eventshared/EventFieldList.vue'

const {t} = useI18n()

const CATEGORIES = [{id: 1, stationId: 'demo', name: 'Übung', position: 0}]
const ATTENDANCE_TEMPLATES = [{id: 1, stationId: 'demo', name: 'Übungsabend'}]
const FIELDS = [
  {name: 'Ort', fieldType: 'STRING', overview: true, isPublic: true, value: 'Gerätehaus'},
  {name: 'Treffpunkt', fieldType: 'STRING', overview: true, isPublic: false, value: 'Fahrzeughalle'},
]
const REMINDERS = [7, 1]

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
                              :requires-registration="true" :requires-confirmation="false"
                              :registration-limit="30"
                              :categories="CATEGORIES" :attendance-templates="ATTENDANCE_TEMPLATES"/>
        <NeutralContainer>
          <EventReminderEditor :model-value="REMINDERS"/>
        </NeutralContainer>
        <NeutralContainer class="space-y-4">
          <EventFieldList :fields="FIELDS" :value-label="t('eventFields.defaultValue')" show-value/>
        </NeutralContainer>
        <SaveButton :action="noop"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplateEdit.defaultsTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.defaultsText') }}</p>
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

    <HelpSection :title="t('helpCenter.eventTemplateEdit.backTitle')">
      <p>{{ t('helpCenter.eventTemplateEdit.backText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventTemplateEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
