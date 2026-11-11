/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import CoverablePicture from '@/components/problem/CoverablePicture.vue'
import {beacon} from '@/api'
import client from '@/api/client'
import {flatten, pictureFrom, useCovers} from '@/composables/useScreenCapture'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import type {ProblemPayload, ReportPayload} from '@/api/generated/schema'
import {describeFailure, FailureKind, type Failure} from '@/util/failure'

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
  /** Whether this report carries a picture, which is the one part not shown as bytes. */
  hasPicture?: boolean
}>()

const emit = defineEmits<{sent: []}>()

const {t} = useI18n()
const payload = ref<ProblemPayload | ReportPayload | null>(null)

/** Whether a fault takes the log lines around it along. Off until the reader ticks it, every time. */
const withContext = ref(false)

/** The occurrence the previewed lines surround, which the send names so the lines shown are the lines sent. */
const contextAt = ref<string | null>(null)

/** Lines were asked for, but the log kept none around the fault, so none go. */
const noLinesKept = computed(() => props.kind === 'problem' && withContext.value && payload.value != null
    && (payload.value as ProblemPayload).context == null)

/**
 * The instance took nothing because its queue is full, which is a wait rather than a fault. Saying so
 * without offering a bug report is the point: nothing here is broken and nothing needs reporting.
 */
function notQueued() {
  sendFailure.value = {
    kind: FailureKind.TOO_OFTEN,
    message: t('beacon.notQueued'),
    guidance: t('beacon.notQueuedGuidance'),
    reportable: false,
  }
}
const sending = ref(false)
const picture = ref<HTMLCanvasElement | null>(null)
const {covers, add, removeAt, clear} = useCovers()

/** The stored picture as pixels, so more of it can be covered before it leaves the instance. */
async function pictureOfReport(id: number): Promise<HTMLCanvasElement | null> {
  try {
    const answer = await client.get(`/admin/problem-reports/${id}/screenshot`, {responseType: 'blob'})
    return await pictureFrom(answer.data as Blob)
  } catch {
    return null
  }
}

/** What went wrong sending, apart from the preview's own failure, so a failed send can be tried again. */
const sendFailure = ref<Failure | null>(null)

const {loading, failure, reload: preview} = useAsyncLoader(async (isCurrent) => {
  const id = props.entryId
  if (id == null) return
  payload.value = null
  picture.value = null
  clear()
  const shot = props.kind === 'report' && props.hasPicture ? await pictureOfReport(id) : null
  if (!isCurrent()) return
  picture.value = shot
  if (props.kind === 'problem') {
    const preview = await beacon.previewProblem(id, withContext.value)
    if (!isCurrent()) return
    contextAt.value = preview.contextAt ?? null
    payload.value = preview.payload
    return
  }
  const answer = await beacon.previewReportPayload(id)
  if (isCurrent()) payload.value = answer
}, {autoLoad: false})

watch(open, value => {
  if (!value) return
  withContext.value = false
  sendFailure.value = null
  if (props.entryId != null) void preview()
})

watch(withContext, () => {
  payload.value = null
  contextAt.value = null
  if (open.value && props.entryId != null) void preview()
})

/**
 * Sends it, and says so only where the instance took it.
 *
 * <p>What comes back is how many were queued, and that is nought when the queue is already full. A
 * dialog that closed on "sent" either way would tell an operator their report had gone when it had
 * been dropped on the floor.
 */
/**
 * Sends it with the picture, further covered where the reader covered more of it.
 *
 * <p>The covers become pixels here, at the moment of sending, and the copy that carries them is let
 * go of on the instance once it has gone: what the reporter covered is what the report keeps.
 */
async function send(withPicture = true) {
  if (props.entryId == null) return
  sending.value = true
  sendFailure.value = null
  try {
    const covered = withPicture && picture.value && covers.value.length > 0
        ? await flatten(picture.value, covers.value)
        : null
    const queued = props.kind === 'problem'
        ? await beacon.sendProblem(props.entryId, withContext.value ? contextAt.value : null)
        : await beacon.sendReportToBeacon(props.entryId, {
          screenshot: covered,
          dropScreenshot: !withPicture,
        })
    if (queued < 1) {
      notQueued()
      return
    }
    emit('sent')
    open.value = false
  } catch (e) {
    sendFailure.value = describeFailure(e, t)
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

      <label v-if="kind === 'problem'" class="flex items-center gap-2 text-sm">
        <CheckboxInput v-model="withContext" data-testid="beacon-with-context"/>
        {{ t('beacon.withContext') }}
      </label>
      <MutedText v-if="kind === 'problem'" tag="p" size="sm">{{ t('beacon.withContextHint') }}</MutedText>
      <MutedText v-if="noLinesKept" tag="p" size="sm" data-testid="beacon-no-lines-kept">{{ t('beacon.noLinesKept') }}</MutedText>

      <Spinner v-if="loading" size="md"/>
      <FailureAlert :failure="failure ?? sendFailure"/>

      <pre v-if="payload" class="max-h-96 overflow-auto rounded-lg bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 p-4 text-xs whitespace-pre-wrap break-words">{{ JSON.stringify(payload, null, 2) }}</pre>

      <div v-if="picture" class="space-y-2">
        <MutedText size="sm" tag="p">{{ t('beacon.pictureGoesWithIt') }}</MutedText>
        <CoverablePicture :covers="covers" :picture="picture" @add="add" @remove="removeAt"/>
      </div>

      <ButtonRow align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <SecondaryButton
            v-if="picture"
            :disabled="sending"
            data-testid="beacon-send-without-picture"
            @click="send(false)"
        >
          {{ t('beacon.sendWithoutPicture') }}
        </SecondaryButton>
        <PrimaryButton :disabled="!payload || sending || loading || failure != null" :icon="['fas', 'tower-broadcast']" @click="send(true)">
          {{ sending ? t('common.loading') : t('beacon.send') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
