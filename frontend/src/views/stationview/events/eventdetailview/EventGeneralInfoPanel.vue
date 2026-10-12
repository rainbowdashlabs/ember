/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {renderMarkdown} from '@/util/markdown'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import EventMetaGrid from './EventMetaGrid.vue'
import type {AppointmentField, StationEvent} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import ProseContent from '@/components/display/ProseContent.vue'

/**
 * The general facts of an appointment: when it begins and ends first, since that is what a reader looks
 * for, then its description, then its template and the answers to its questions.
 */
defineProps<{
  event: StationEvent
  eventId: number
  fields: AppointmentField[]
  allMembers: MemberLike[]
  currentMemberId: number
  startFormatted: string
  endFormatted: string
  templateName: string
  canEditEvent: boolean
  /** The occurrence on screen, which is the one a question answered per date is answered for. */
  effectiveDate?: string | null
}>()

const emit = defineEmits<{
  (e: 'field-updated', field: AppointmentField): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('events.general') }}</SubHeader>
    <div class="grid grid-cols-2 gap-4">
      <div>
        <DetailLabel>{{ t('events.startTime') }}</DetailLabel>
        <p class="text-sm" data-testid="event-start">{{ startFormatted }}</p>
      </div>
      <div>
        <DetailLabel>{{ t('events.endTime') }}</DetailLabel>
        <p class="text-sm" data-testid="event-end">{{ endFormatted }}</p>
      </div>
    </div>
    <div>
      <DetailLabel>{{ t('events.description') }}</DetailLabel>
      <ProseContent v-if="event.description" class="mt-1" v-html="renderMarkdown(event.description)"/>
      <p v-else class="text-sm">–</p>
    </div>
    <EventMetaGrid
        :event-id="eventId"
        :fields="fields"
        :all-members="allMembers"
        :current-member-id="currentMemberId"
        :template-name="templateName"
        :can-edit-event="canEditEvent"
        :effective-date="effectiveDate"
        @field-updated="(f) => emit('field-updated', f)"
    />
  </NeutralContainer>
</template>
