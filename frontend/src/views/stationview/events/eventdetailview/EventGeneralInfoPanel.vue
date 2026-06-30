/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {marked} from 'marked'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import EventMetaGrid from './EventMetaGrid.vue'
import type {EventField, StationEvent, StationMember} from '@/api/types'

defineProps<{
  event: StationEvent
  eventId: number
  fields: EventField[]
  allMembers: StationMember[]
  currentMemberId: number
  startFormatted: string
  endFormatted: string
  categoryName: string
  templateName: string
  canManageEvents: boolean
}>()

const emit = defineEmits<{
  (e: 'field-updated', field: EventField): void
}>()

const {t} = useI18n()

function renderMarkdown(md: string): string {
  try { return marked.parse(md) as string } catch { return md }
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('events.general') }}</SubHeader>
    <div>
      <DetailLabel>{{ t('events.description') }}</DetailLabel>
      <div v-if="event.description" class="prose prose-sm dark:prose-invert max-w-none mt-1" v-html="renderMarkdown(event.description)"/>
      <p v-else class="text-sm">–</p>
    </div>
    <EventMetaGrid
        :event-id="eventId"
        :fields="fields"
        :all-members="allMembers"
        :current-member-id="currentMemberId"
        :start-formatted="startFormatted"
        :end-formatted="endFormatted"
        :category-name="categoryName"
        :template-name="templateName"
        :can-manage-events="canManageEvents"
        @field-updated="(f) => emit('field-updated', f)"
    />
  </NeutralContainer>
</template>
