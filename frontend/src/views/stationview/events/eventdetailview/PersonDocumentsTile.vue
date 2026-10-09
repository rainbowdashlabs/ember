/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {ParticipantDocuments} from '@/api/generated/schema'
import PersonDocument from './PersonDocument.vue'
import type {ParticipantCopy} from './documentTiles'

/**
 * Everything the appointment asks one person the reader acts for to bring: their name, then each document
 * with its download, scan upload and online signing, and where it stands.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  person: ParticipantDocuments
  busy: boolean
}>()

const emit = defineEmits<{
  fetch: [copy: ParticipantCopy]
  handIn: [copy: ParticipantCopy, file: File]
  changed: []
}>()

function copyOf(document: ParticipantDocuments['documents'][number]): ParticipantCopy {
  return {memberId: props.person.memberId, name: props.person.name, document}
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="person-documents">
    <span class="block font-semibold">{{ person.name }}</span>
    <PersonDocument v-for="document in person.documents" :key="document.templateId" :event-id="eventId" :date="date"
                    :copy="copyOf(document)" :busy="busy" @fetch="emit('fetch', copyOf(document))"
                    @hand-in="file => emit('handIn', copyOf(document), file)" @changed="emit('changed')"/>
  </NeutralContainer>
</template>
