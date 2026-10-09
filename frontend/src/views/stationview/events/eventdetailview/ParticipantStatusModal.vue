/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {appointmentDocuments} from '@/api'
import type {PaperSubmission, PartnerSigner, RequiredTemplate} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {downloadAuthed} from '@/util/downloadAuthed'
import ParticipantScanRow from './ParticipantScanRow.vue'
import PartnerSignerList from './PartnerSignerList.vue'
import RejectScanModal from './RejectScanModal.vue'
import type {ParticipantCopy} from './documentTiles'

/**
 * Where every participant of the date stands with one document, for an event manager. A scan handed in
 * is read, confirmed as the signed paper copy or turned down with a reason here; a scan the manager hands
 * in for a participant counts as confirmed at once. Members of partner stations, who sign at their own
 * station, are listed below with the copy that came back or a paper copy to confirm.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  template: RequiredTemplate
  participants: ParticipantCopy[]
  /** The members partner stations registered, with where they stand with every document partners sign. */
  partnerSigners: PartnerSigner[]
  /** Called once a scan was confirmed, turned down or handed in, or a partner's paper copy confirmed. */
  onChanged: () => void
}>()

const {t} = useI18n()

const rejecting = ref<PaperSubmission | null>(null)
const reason = ref('')
const rejectOpen = computed({
  get: () => rejecting.value !== null,
  set: (shown: boolean) => {
    if (!shown) rejecting.value = null
  },
})

const viewing = useAsyncAction(async (paper: PaperSubmission) => {
  await downloadAuthed(appointmentDocuments.scanContentUrl(props.eventId, paper.id))
})

const deciding = useAsyncAction(async (decide: () => Promise<unknown>) => {
  await decide()
  rejecting.value = null
  props.onChanged()
})

function confirm(paper: PaperSubmission) {
  void deciding.run(() => appointmentDocuments.confirmScan(props.eventId, paper.id))
}

function startRejecting(paper: PaperSubmission) {
  reason.value = ''
  rejecting.value = paper
}

function reject() {
  const paper = rejecting.value
  if (paper) void deciding.run(() => appointmentDocuments.rejectScan(props.eventId, paper.id, reason.value))
}

function handIn(copy: ParticipantCopy, file: File) {
  const target = {eventId: props.eventId, date: props.date, templateId: props.template.templateId, memberId: copy.memberId}
  const title = t('events.documents.scanTitle', {name: props.template.name})
  void deciding.run(() => appointmentDocuments.submitScan(target, file, title))
}
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-3" data-testid="documents-to-bring-overview">
      <SubHeader>{{ t('events.documents.overviewOf', {name: template.name}) }}</SubHeader>
      <FailureAlert :failure="deciding.failure.value ?? viewing.failure.value"/>
      <MutedText v-if="participants.length === 0 && partnerSigners.length === 0" size="sm" tag="p">
        {{ t('events.documents.overviewEmpty') }}
      </MutedText>
      <ul v-if="participants.length > 0" class="space-y-3">
        <ParticipantScanRow v-for="participant in participants" :key="participant.memberId" :copy="participant"
                            :busy="deciding.running.value" @view="viewing.run" @confirm="confirm"
                            @reject="startRejecting" @hand-in="file => handIn(participant, file)"/>
      </ul>
      <PartnerSignerList :event-id="eventId" :template-id="template.templateId" :signers="partnerSigners"
                         :on-changed="onChanged"/>
    </div>
    <RejectScanModal v-if="rejecting" v-model="rejectOpen" v-model:reason="reason"
                     :processing="deciding.running.value" @submit="reject"/>
  </Modal>
</template>
