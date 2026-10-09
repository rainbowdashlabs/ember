/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {appointmentDocuments} from '@/api'
import {PaperState} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {downloadAuthed} from '@/util/downloadAuthed'
import {showToast} from '@/util/toast'
import RejectScanModal from './RejectScanModal.vue'
import UnsignableFields from './UnsignableFields.vue'
import {isSettled} from './documentGroups'
import {SCAN_TYPES, type ParticipantCopy} from './documentTiles'
import {scanReceiptKey} from './scanReceipt'

/**
 * What a manager of the registrations does with one participant's copy. The copy downloads as its sealed
 * version once it was signed online, and a scan handed in for it downloads too. A waiting scan is
 * confirmed or turned down with a reason; until the copy is settled the manager may hand in a scan for the
 * participant, which counts as confirmed on paper at once. Fields nobody can sign are settled below.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  copy: ParticipantCopy
  /** Called once the copy changed, so the documents are read again. */
  onChanged: () => void
}>()

const {t} = useI18n()

const rejecting = ref(false)
const reason = ref('')

const target = computed(() => ({
  eventId: props.eventId,
  date: props.date,
  templateId: props.copy.document.templateId,
  memberId: props.copy.memberId,
}))
const paper = computed(() => props.copy.document.paper)
const waiting = computed(() => paper.value?.state === PaperState.SUBMITTED ? paper.value : null)
const scan = computed(() => paper.value && paper.value.state !== PaperState.WITHDRAWN ? paper.value : null)
const handsIn = computed(() => !waiting.value && !isSettled(props.copy.document))

const downloading = useAsyncAction(async (url: string) => {
  await downloadAuthed(url)
})

const deciding = useAsyncAction(async (decide: () => Promise<unknown>) => {
  await decide()
  rejecting.value = false
  props.onChanged()
})

function downloadCopy() {
  void downloading.run(appointmentDocuments.participantCopyUrl(target.value))
}

function downloadScan() {
  if (scan.value) void downloading.run(appointmentDocuments.scanContentUrl(props.eventId, scan.value.id))
}

function confirm() {
  const submission = waiting.value
  if (submission) void deciding.run(() => appointmentDocuments.confirmScan(props.eventId, submission.id))
}

function startRejecting() {
  reason.value = ''
  rejecting.value = true
}

function reject() {
  const submission = waiting.value
  if (submission) void deciding.run(() => appointmentDocuments.rejectScan(props.eventId, submission.id, reason.value))
}

function handIn(file: File) {
  const title = t('events.documents.scanTitle', {name: props.copy.document.name})
  void deciding.run(async () => {
    const received = await appointmentDocuments.submitScan(target.value, file, title)
    showToast(t(scanReceiptKey(received)), 'success')
  })
}
</script>

<template>
  <div class="space-y-2" data-testid="copy-review">
    <FailureAlert :failure="deciding.failure.value ?? downloading.failure.value"/>
    <ButtonRow>
      <SecondaryButton v-if="copy.document.documentId !== null" compact :icon="['fas', 'download']"
                       :disabled="downloading.running.value" data-testid="copy-review-download" @click="downloadCopy">
        {{ t('common.download') }}
      </SecondaryButton>
      <SecondaryButton v-if="scan" compact :icon="['fas', 'download']" :disabled="downloading.running.value"
                       data-testid="copy-review-scan" @click="downloadScan">
        {{ t('events.documents.review.scanDownload') }}
      </SecondaryButton>
      <SuccessButton v-if="waiting" compact :icon="['fas', 'check']" :disabled="deciding.running.value"
                     data-testid="document-scan-confirm" @click="confirm">
        {{ t('events.documents.scanConfirm') }}
      </SuccessButton>
      <ErrorButton v-if="waiting" compact :icon="['fas', 'xmark']" :disabled="deciding.running.value"
                   data-testid="document-scan-reject" @click="startRejecting">
        {{ t('events.documents.scanReject') }}
      </ErrorButton>
      <FileUploadButton v-if="handsIn" compact :accept="SCAN_TYPES" :disabled="deciding.running.value"
                        data-testid="document-scan-hand-in" @select="handIn">
        {{ t('events.documents.scanUploadConfirmed') }}
      </FileUploadButton>
    </ButtonRow>
    <UnsignableFields :copy="copy" :on-changed="onChanged"/>
    <RejectScanModal v-if="rejecting" v-model="rejecting" v-model:reason="reason"
                     :processing="deciding.running.value" @submit="reject"/>
  </div>
</template>
