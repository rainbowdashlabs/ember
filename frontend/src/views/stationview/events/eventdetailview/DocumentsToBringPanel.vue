/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import {appointmentDocuments, partnerAgreements} from '@/api'
import type {AppointmentDocuments, PaperSubmission, PartnerSigner} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {fetchCopy} from './documentsToBring'
import {copiesOf, signsOnline, type ParticipantCopy} from './documentTiles'
import {scanReceiptKey, withScan} from './scanReceipt'
import DocumentToBringTile from './DocumentToBringTile.vue'
import PersonDocumentsTile from './PersonDocumentsTile.vue'
import PersonDocument from './PersonDocument.vue'
import ParticipantDocumentsSection from './ParticipantDocumentsSection.vue'

/**
 * The documents the appointment asks participants to bring on the date. The reader and every member in
 * their care who takes part (on an appointment without registrations: all of them) get one tile per
 * person, with each document to download filled with the person's data, sign online, or print, sign and
 * bring; getting it the first time files it in the person's documents. Instead of bringing the signed
 * paper, they may hand in its scan here, which waits for an event manager to confirm it and can be taken
 * back meanwhile. Whoever takes no part, and every event manager, sees one tile per document with a
 * picture of its first page. Below, an event manager gets a section per document with where every
 * participant stands, the members of partner stations included ({@link ParticipantDocumentsSection}).
 * Nothing shows where the appointment asks for nothing.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
}>()

const {t} = useI18n()

const documents = ref<AppointmentDocuments | null>(null)
const partnerSigners = ref<PartnerSigner[]>([])
const required = computed(() => documents.value?.required ?? [])
const takesPart = computed(() => (documents.value?.own.length ?? 0) > 0)
const showsDocumentTiles = computed(() => !takesPart.value || documents.value?.participants != null)
const hint = computed(() => {
  if (!takesPart.value) return t('events.documents.toBringOthersHint')
  return documents.value && signsOnline(documents.value)
      ? t('events.documents.toBringHint')
      : t('events.documents.toBringPaperHint')
})

const {failure, reload} = useAsyncLoader(async isCurrent => {
  const loaded = await appointmentDocuments.documentsToBring(props.eventId, props.date)
  const partners = loaded.participants === null || loaded.required.length === 0
      ? []
      : await partnerAgreements.listSigners(props.eventId, props.date)
  if (!isCurrent()) return
  documents.value = loaded
  partnerSigners.value = partners
}, {autoLoad: false})

const fetching = useAsyncAction(async (copy: ParticipantCopy) => {
  await fetchCopy(props.eventId, props.date, copy.memberId, copy.document)
  await reload()
})

const received = ref<PaperSubmission | null>(null)

const handingIn = useAsyncAction(async (copy: ParticipantCopy, file: File) => {
  received.value = null
  const target = {eventId: props.eventId, date: props.date, templateId: copy.document.templateId, memberId: copy.memberId}
  const paper = await appointmentDocuments.submitScan(target, file, t('events.documents.scanTitle', {name: copy.document.name}))
  if (documents.value) documents.value = withScan(documents.value, paper)
  received.value = paper
  await reload()
})

const takingBack = useAsyncAction(async (copy: ParticipantCopy) => {
  const paper = copy.document.paper
  if (!paper) return
  received.value = null
  await appointmentDocuments.withdrawScan(props.eventId, paper.id)
  await reload()
})

const busy = computed(() => fetching.running.value || handingIn.running.value || takingBack.running.value)
const actionFailure = computed(() => fetching.failure.value ?? handingIn.failure.value ?? takingBack.failure.value)

watch(() => [props.eventId, props.date], reload, {immediate: true})
</script>

<template>
  <div v-if="required.length > 0 || failure" class="space-y-6">
    <NeutralContainer class="space-y-3" data-testid="documents-to-bring">
      <SubHeader>{{ t('events.documents.toBringTitle') }}</SubHeader>
      <MutedText size="sm" tag="p">{{ hint }}</MutedText>
      <FailureAlert :failure="failure ?? actionFailure"/>
      <Alert v-if="received" variant="success" data-testid="document-scan-received">
        {{ t(scanReceiptKey(received)) }}
      </Alert>
      <div v-if="takesPart && documents" class="grid grid-cols-1 gap-3 md:grid-cols-2">
        <PersonDocumentsTile v-for="person in documents.own" :key="person.memberId" :name="person.name">
          <PersonDocument v-for="copy in copiesOf(person)" :key="copy.document.templateId" :event-id="eventId"
                          :date="date" :copy="copy" :busy="busy" @fetch="fetching.run(copy)"
                          @hand-in="file => handingIn.run(copy, file)" @withdraw-scan="takingBack.run(copy)"
                          @changed="reload"/>
        </PersonDocumentsTile>
      </div>
      <div v-if="showsDocumentTiles" class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <DocumentToBringTile v-for="template in required" :key="template.templateId" :event-id="eventId"
                             :template="template"/>
      </div>
    </NeutralContainer>
    <template v-if="documents?.participants">
      <ParticipantDocumentsSection v-for="template in required" :key="template.templateId" :event-id="eventId"
                                   :date="date" :template="template" :participants="documents.participants"
                                   :partner-signers="partnerSigners" :on-changed="reload"/>
    </template>
  </div>
</template>
