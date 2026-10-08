/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {signing} from '@/api'
import {contentUrl} from '@/api/documents'
import {RequestState, type ManagedRequestResponse} from '@/api/generated/schema'
import type {FieldSettlement} from '@/api/signing'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {downloadAuthed} from '@/util/downloadAuthed'
import {showToast} from '@/util/toast'
import SignatureCorrectionPicker from './SignatureCorrectionPicker.vue'
import SignatureFieldRow from './SignatureFieldRow.vue'

/**
 * A request for signatures as the manager of its document looks after it: each field with its signer, its
 * statement and the act that signed it, and what no signing act settles. A field signed on paper is
 * confirmed, a field nobody needs is waived, one nobody should be asked for any more is withdrawn, the
 * whole request can be withdrawn, and after a change it is asked anew on the corrected document.
 *
 * <p>Every action answers the request as it then stands, which is shown at once; the document's next
 * sealed version shows it a few minutes later. A request replaced by a correction leads on to its
 * replacement.
 */
const props = defineProps<{
  requestUid: string
  /** Told after every change, so the list the request was opened from can show it. */
  onChanged?: () => void
}>()

const {t} = useI18n()

const shownUid = ref(props.requestUid)
const request = ref<ManagedRequestResponse | null>(null)
const withdrawing = ref(false)

watch(() => props.requestUid, uid => {
  shownUid.value = uid
})

const loader = useAsyncLoader(async isCurrent => {
  const loaded = await signing.getSignatureRequest(shownUid.value)
  if (isCurrent()) request.value = loaded
}, {autoLoad: false})

watch(shownUid, uid => {
  if (request.value?.uid !== uid) void loader.reload()
}, {immediate: true})

const open = computed(() => request.value?.state === RequestState.OPEN)

/** Runs one action on the request, shows what it answers and tells the list. */
const acting = useAsyncAction(async (change: () => Promise<ManagedRequestResponse>, done: string) => {
  const changed = await change()
  request.value = changed
  shownUid.value = changed.uid
  withdrawing.value = false
  showToast(done, 'success')
  props.onChanged?.()
})

/** Saves the document as it is served now: its current sealed version, or the file as filed before any seal. */
async function download() {
  const shown = request.value
  if (shown?.documentId == null) return
  await downloadAuthed(contentUrl(shown.documentId), `${shown.documentTitle ?? shown.memberName}.pdf`)
}

function followReplacement() {
  const replacement = request.value?.supersededBy
  if (replacement) shownUid.value = replacement
}

function settle(fieldName: string, settlement: FieldSettlement) {
  void acting.run(
      () => signing.settleSignatureField(shownUid.value, fieldName, settlement), t('signing.manage.settledToast'))
}

function withdraw() {
  void acting.run(() => signing.withdrawSignatureRequest(shownUid.value), t('signing.manage.withdrawnToast'))
}

function rectify(generationId: number) {
  void acting.run(
      () => signing.rectifySignatureRequest(shownUid.value, generationId), t('signing.manage.correction.done'))
}
</script>

<template>
  <div class="space-y-4" data-testid="signature-request">
    <SubHeader>{{ t('signing.manage.heading') }}</SubHeader>
    <Spinner v-if="loader.loading.value"/>
    <FailureAlert :failure="loader.failure.value ?? acting.failure.value"/>
    <template v-if="request">
      <MutedText size="sm" tag="p">{{ t(`signing.ask.requestState.${request.state}`) }}</MutedText>
      <SecondaryButton v-if="request.documentId !== null" :icon="['fas', 'download']"
                       data-testid="signature-request-download" @click="download">
        {{ t('signing.manage.download') }}
      </SecondaryButton>
      <div v-if="request.supersededBy" class="space-y-1">
        <MutedText size="sm" tag="p">{{ t('signing.manage.replaced') }}</MutedText>
        <LinkButton data-testid="signature-request-replacement" @click="followReplacement">
          {{ t('signing.manage.showReplacement') }}
        </LinkButton>
      </div>
      <ul class="space-y-3">
        <SignatureFieldRow v-for="field in request.fields" :key="field.fieldName" :field="field"
                           :settleable="open" :busy="acting.running.value"
                           @settle="settlement => settle(field.fieldName, settlement)"/>
      </ul>
      <SignatureCorrectionPicker v-if="request.corrections.length > 0" :corrections="request.corrections"
                                 :busy="acting.running.value" @rectify="rectify"/>
      <div v-if="open && withdrawing" class="space-y-2" data-testid="signature-request-withdraw-confirm">
        <MutedText size="sm" tag="p">{{ t('signing.manage.withdrawAllConfirm') }}</MutedText>
        <ButtonRow pair>
          <SecondaryButton @click="withdrawing = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton :disabled="acting.running.value" data-testid="signature-request-withdraw-yes" @click="withdraw">
            {{ t('signing.manage.withdrawAll') }}
          </ErrorButton>
        </ButtonRow>
      </div>
      <SecondaryButton v-else-if="open" data-testid="signature-request-withdraw" @click="withdrawing = true">
        {{ t('signing.manage.withdrawAll') }}
      </SecondaryButton>
    </template>
  </div>
</template>
