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
import type {EventFieldEntry, EventGroupDetails, MemberGroup, MemberWithName, UserTag} from '@/api/generated/schema'
import EventReminderEditor from '../eventshared/EventReminderEditor.vue'
import EventGroupDateSettingsSection from './EventGroupDateSettingsSection.vue'
import EventGroupColorField from './EventGroupColorField.vue'
import EventGroupFieldsCard from './EventGroupFieldsCard.vue'
import type {useEventGroupSettings} from './useEventGroupSettings'
import {useEventGroupDetails} from './useEventGroupDetails'

/**
 * What an event group is, in the order the appointment form asks it: the name, then category,
 * attendance template and colour in one row, the fields every part shows followed by the template's
 * pre-fills, the description and the reminders.
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
        <EventGroupFieldsCard
            v-model="fields"
            :all-members="members"
            :groups="groups"
            :group-members="groupMembers"
            :tags="tags"
            compact
        />
      </template>
    </EventGroupDateSettingsSection>
    <div class="space-y-1">
      <FieldLabel>{{ t('eventGroups.description') }}</FieldLabel>
      <MarkdownEditor v-model="description" :placeholder="t('events.descriptionPlaceholder')"/>
    </div>
    <EventReminderEditor :model-value="settings.reminders.value" compact @update:model-value="settings.setReminders"/>
  </div>
</template>
