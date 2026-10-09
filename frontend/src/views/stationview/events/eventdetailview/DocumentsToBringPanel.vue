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
import {appointmentDocuments, partnerAgreements} from '@/api'
import type {AppointmentDocuments, PartnerSigner} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {fetchCopy} from './documentsToBring'
import {documentTiles, type ParticipantCopy} from './documentTiles'
import DocumentToBringTile from './DocumentToBringTile.vue'

/**
 * The documents the appointment asks participants to bring on the date, one tile each with a picture
 * of its first page. Everyone who sees the appointment sees them. The reader and every member in their
 * care who takes part (on an appointment without registrations: all of them) download a copy filled
 * with the participant's data to print and sign; getting it the first time files it in the
 * participant's documents. Instead of bringing the signed paper, they may hand in its scan here, which
 * waits for an event manager to confirm it. An event manager opens from each tile where every
 * participant stands, the members of partner stations included. Nothing shows where the appointment asks
 * for nothing.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
}>()

const {t} = useI18n()

const documents = ref<AppointmentDocuments | null>(null)
const partnerSigners = ref<PartnerSigner[]>([])
const tiles = computed(() => documents.value ? documentTiles(documents.value) : [])
const takesPart = computed(() => (documents.value?.own.length ?? 0) > 0)

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

const handingIn = useAsyncAction(async (copy: ParticipantCopy, file: File) => {
  const target = {eventId: props.eventId, date: props.date, templateId: copy.document.templateId, memberId: copy.memberId}
  await appointmentDocuments.submitScan(target, file, t('events.documents.scanTitle', {name: copy.document.name}))
  await reload()
})

const busy = computed(() => fetching.running.value || handingIn.running.value)
const actionFailure = computed(() => fetching.failure.value ?? handingIn.failure.value)

watch(() => [props.eventId, props.date], reload, {immediate: true})
</script>

<template>
  <NeutralContainer v-if="tiles.length > 0 || failure" class="space-y-3" data-testid="documents-to-bring">
    <SubHeader>{{ t('events.documents.toBringTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">
      {{ takesPart ? t('events.documents.toBringHint') : t('events.documents.toBringOthersHint') }}
    </MutedText>
    <FailureAlert :failure="failure ?? actionFailure"/>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <DocumentToBringTile v-for="tile in tiles" :key="tile.template.templateId" :event-id="eventId" :date="date"
                           :tile="tile" :partner-signers="partnerSigners" :busy="busy" :on-changed="reload"
                           @fetch="fetching.run"
                           @hand-in="handingIn.run"/>
    </div>
  </NeutralContainer>
</template>
