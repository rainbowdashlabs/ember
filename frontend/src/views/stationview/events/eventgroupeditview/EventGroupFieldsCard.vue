/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HintedSubHeader from '@/components/typography/HintedSubHeader.vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldList from '../eventshared/EventFieldList.vue'
import {OfferedFieldTypes} from '@/api/fieldTypes'
import type {AttendanceTemplateField, EventFieldEntry, MemberGroup, UserTag} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'

/**
 * The fields an event group hands down to every appointment of it, such as its place or the team
 * looking after it. A member field can let members put themselves in, for the whole group or for one
 * of its appointments; nothing is answered per date, since every appointment of a group is one date.
 * A field tied to a field of the event group's attendance sheet fills it in on the sheet of every
 * appointment, and the sheet's fields are offered to take over.
 */
const fields = defineModel<EventFieldEntry[]>({required: true})

defineProps<{
  /** The fields of the event group's attendance sheet, none without one. */
  attendanceFields?: AttendanceTemplateField[]
  allMembers?: MemberLike[]
  groups?: MemberGroup[]
  groupMembers?: Map<number, MemberLike[]>
  tags?: UserTag[]
  /**
   * Inside the event group's form: no card of its own, since it stands within the form's first
   * section, the hint behind an icon and the fields as compact lines.
   */
  compact?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <component :is="compact ? 'div' : NeutralContainer" class="space-y-3" data-testid="event-group-fields">
    <HintedSubHeader v-if="compact" :hint="t('eventGroups.fieldsHint')">{{ t('eventGroups.fields') }}</HintedSubHeader>
    <template v-else>
      <SectionHeader>{{ t('eventGroups.fields') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('eventGroups.fieldsHint') }}</MutedText>
    </template>
    <EventFieldList
        v-model:fields="fields"
        :compact="compact"
        :hide-heading="compact"
        show-value
        :offered-types="OfferedFieldTypes.EVENT_GROUP"
        :attendance-fields="attendanceFields"
        :all-members="allMembers"
        :groups="groups"
        :group-members="groupMembers"
        :tags="tags"
    />
  </component>
</template>
