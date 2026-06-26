/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import CommentSection from '@/components/comment/CommentSection.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import EventGeneralInfoPanel from './EventGeneralInfoPanel.vue'
import type {EventField, StationEvent} from '@/api/types'
import {isRecurringEvent, StationPermission} from '@/api/types'
import type {AbsentMember} from '@/api/events'

const props = defineProps<{
  event: StationEvent
  eventId: number
  fields: EventField[]
  absentMembers: AbsentMember[]
  focusedDate: string | null
  startFormatted: string
  endFormatted: string
  categoryName: string
  templateName: string
  canManageEvents: boolean
  canManageAttendance: boolean
  hasPermission: (perm: string) => boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <EventGeneralInfoPanel
        :event="event"
        :fields="fields"
        :start-formatted="startFormatted"
        :end-formatted="endFormatted"
        :category-name="categoryName"
        :template-name="templateName"
        :can-manage-events="canManageEvents"
    />

    <NeutralContainer v-if="isRecurringEvent(event.eventType) && (canManageEvents || canManageAttendance)" class="space-y-3">
      <SubHeader>{{ t('eventDetail.absentMembers') }}</SubHeader>
      <div v-if="absentMembers.length > 0" class="flex flex-wrap gap-2">
        <ErrorBadge v-for="m in absentMembers" :key="m.memberId">{{ m.memberName }}<span v-if="m.reason" class="ml-1 opacity-75">– {{ m.reason }}</span></ErrorBadge>
      </div>
      <p v-else class="text-sm text-(--text-muted)">{{ t('eventDetail.noAbsences') }}</p>
    </NeutralContainer>

    <NeutralContainer v-if="hasPermission(StationPermission.EVENT_EDIT)">
      <NoteEditor entity-type="EVENT" :entity-id="eventId"/>
    </NeutralContainer>

    <NeutralContainer>
      <CommentSection :event-id="eventId" :event-date="focusedDate"/>
    </NeutralContainer>
  </div>
</template>
