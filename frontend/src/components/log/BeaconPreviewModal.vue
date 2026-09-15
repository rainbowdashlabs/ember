/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {beacon} from '@/api'
import type {ProblemPayload, ReportPayload} from '@/api/beacon'

/**
 * Exactly what would leave this instance, shown before it does.
 *
 * <p>A fault quotes what failed and a report quotes a person, and either can carry an address or a
 * row somebody is recognised by. Asking an operator to agree to forwarding without showing them the
 * bytes would be asking them to agree to something nobody has read. So both kinds are shown the same
 * way, by the same dialog: what differs between them is which pair of calls it makes, and that is not
 * worth a second file.
 */
const open = defineModel<boolean>('open', {default: false})

const props = defineProps<{
  /** Whether the entry is a fault out of the log or a report somebody wrote. */
  kind: 'problem' | 'report'
  entryId: number | null
}>()

const emit = defineEmits<{sent: []}>()

const {t} = useI18n()
const payload = ref<ProblemPayload | ReportPayload | null>(null)
const loading = ref(false)
const error = ref('')
const sending = ref(false)

watch(open, async value => {
  if (!value || props.entryId == null) return
  loading.value = true
  error.value = ''
  payload.value = null
  try {
    payload.value = props.kind === 'problem'
        ? await beacon.previewProblem(props.entryId)
        : await beacon.previewReportPayload(props.entryId)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
})

/**
 * Sends it, and says so only where the instance took it.
 *
 * <p>What comes back is how many were queued, and that is nought when the queue is already full. A
 * dialog that closed on "sent" either way would tell an operator their report had gone when it had
 * been dropped on the floor.
 */
async function send() {
  if (props.entryId == null) return
  sending.value = true
  try {
    const queued = props.kind === 'problem'
        ? await beacon.sendProblem(props.entryId)
        : await beacon.sendReportToBeacon(props.entryId)
    if (queued < 1) {
      error.value = t('beacon.notQueued')
      return
    }
    emit('sent')
    open.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-4">
      <SubHeader>{{ t('beacon.previewTitle') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('beacon.previewHint') }}</MutedText>

      <Spinner v-if="loading" size="md"/>
      <FailureAlert :message="error"/>

      <pre v-if="payload" class="max-h-96 overflow-auto rounded-lg bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 p-4 text-xs whitespace-pre-wrap break-words">{{ JSON.stringify(payload, null, 2) }}</pre>

      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!payload || sending" :icon="['fas', 'tower-broadcast']" @click="send">
          {{ sending ? t('common.loading') : t('beacon.send') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
