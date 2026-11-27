/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FormSection from '@/components/container/FormSection.vue'
import type {EventFieldEntry, EventGroupDetails, MemberGroup, MemberWithName, UserTag} from '@/api/generated/schema'
import EventReminderEditor from '../eventshared/EventReminderEditor.vue'
import EventGroupDateSettingsSection from './EventGroupDateSettingsSection.vue'
import EventGroupColorField from './EventGroupColorField.vue'
import EventGroupFieldsCard from './EventGroupFieldsCard.vue'
import type {useEventGroupSettings} from './useEventGroupSettings'
import {useEventGroupDetails} from './useEventGroupDetails'
import {fieldsSummary, remindersSummary} from '../eventshared/sectionSummaries'

/**
 * What an event group is, in the order the appointment form asks it: the name, then category,
 * attendance template and colour in one row, the fields every part shows followed by the template's
 * pre-fills, the description and the reminders. The fields and the reminders are folded sections
 * that show what they hold.
 */
const details = defineModel<EventGroupDetails>('details', {required: true})
const fields = defineModel<EventFieldEntry[]>('fields', {required: true})

defineProps<{
  eventGroupId: number | null
  settings: ReturnType<typeof useEventGroupSettings>
  members: MemberWithName[]
  groups: MemberGroup[]
  groupMembers: Map<number, MemberWithName[]>
  tags: UserTag[]
}>()

const {name, description} = useEventGroupDetails(details)

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <TextInput
        v-model="name"
        :aria-label="t('eventGroups.name')"
        :placeholder="t('eventGroups.namePlaceholder')"
        class="text-lg font-semibold"
        data-testid="event-group-name"
    />
    <EventGroupDateSettingsSection :settings="settings" compact>
      <template #row-end>
        <EventGroupColorField v-model="details" :event-group-id="eventGroupId"/>
      </template>
      <template #after-row>
        <FormSection :title="t('formSections.fieldsOnly')" :summary="fieldsSummary('', fields.length, t)" data-testid="form-section-fields">
          <EventGroupFieldsCard
              v-model="fields"
              :attendance-fields="settings.templateFields.value"
              :all-members="members"
              :groups="groups"
              :group-members="groupMembers"
              :tags="tags"
              compact
          />
        </FormSection>
      </template>
    </EventGroupDateSettingsSection>
    <div class="space-y-1">
      <FieldLabel>{{ t('eventGroups.description') }}</FieldLabel>
      <MarkdownEditor v-model="description" :placeholder="t('events.descriptionPlaceholder')"/>
    </div>
    <FormSection
        :title="t('formSections.reminders')"
        :summary="remindersSummary(settings.reminders.value, t)"
        data-testid="form-section-reminders"
    >
      <EventReminderEditor :model-value="settings.reminders.value" compact @update:model-value="settings.setReminders"/>
    </FormSection>
  </div>
</template>
