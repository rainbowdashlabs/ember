/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {appointmentDocuments} from '@/api'
import type {ParticipantDocuments, RequiredDocumentStatus} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeFailure, type Failure} from '@/util/failure'
import {fetchCopy} from './documentsToBring'
import DocumentToBringRow from './DocumentToBringRow.vue'

/**
 * The documents the appointment asks participants to bring, for the reader and every member in their
 * care who takes part on the date: a copy filled with the participant's data to download, print and
 * sign. Getting it the first time files it in the participant's documents. Nothing shows for a reader
 * nobody of whose takes part, or where the appointment asks for nothing.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
}>()

const {t} = useI18n()

const participants = ref<ParticipantDocuments[]>([])
const failure = ref<Failure | null>(null)

async function load() {
  try {
    participants.value = await appointmentDocuments.documentsToBring(props.eventId, props.date)
    failure.value = null
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

const fetching = useAsyncAction(async (memberId: number, document: RequiredDocumentStatus) => {
  await fetchCopy(props.eventId, props.date, memberId, document)
  await load()
})

watch(() => [props.eventId, props.date], load, {immediate: true})
</script>

<template>
  <NeutralContainer v-if="participants.length > 0 || failure" class="space-y-3" data-testid="documents-to-bring">
    <SubHeader>{{ t('events.documents.toBringTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('events.documents.toBringHint') }}</MutedText>
    <FailureAlert :failure="failure ?? fetching.failure.value"/>
    <div v-for="participant in participants" :key="participant.memberId" class="space-y-2">
      <span class="font-semibold">{{ participant.name }}</span>
      <DocumentToBringRow v-for="document in participant.documents" :key="document.templateId"
                          :document="document" :busy="fetching.running.value"
                          @fetch="fetching.run(participant.memberId, document)"/>
    </div>
  </NeutralContainer>
</template>
