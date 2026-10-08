/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SigningUnavailable from './signingview/SigningUnavailable.vue'
import SigningDocument from './signingview/SigningDocument.vue'
import SigningStatement from './signingview/SigningStatement.vue'
import SigningProof from './signingview/SigningProof.vue'
import SigningDone from './signingview/SigningDone.vue'
import {useSigningAct, type TypedProof} from './signingview/useSigningAct'
import {describeSigningFailure} from './signingview/signingFailure'
import type {OpenSignatureResponse} from '@/api/generated/schema'
import {getSigningField} from '@/api/signing'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {Failure} from '@/util/failure'

/**
 * Signing one field of a document: reading the document, confirming the statement, and giving the
 * proof that binds the act.
 *
 * <p>Three steps on one page, each a section under its own heading, so a screen reader can move between
 * them by heading and a keyboard reaches everything in reading order. The proof step appears only once
 * the statement is ticked and the act is started, and takes the focus when it does; the result replaces
 * the steps and takes the focus in turn. What changes without a move of focus is said in the live region
 * at the top.
 *
 * <p>The address is what a "please sign" link opens, so the page loads the field itself and says plainly
 * when it no longer waits for the reader, was never theirs, or is gone.
 */
const {t} = useI18n()
const route = useRoute()
const fieldId = computed(() => Number(route.params.fieldId))

const field = ref<OpenSignatureResponse | null>(null)
const confirmed = ref(false)
const documentReady = ref(false)
const announcement = ref('')
const proofStep = ref<InstanceType<typeof SigningProof> | null>(null)

const act = useSigningAct(() => fieldId.value)

const {loading, failure: loadFailure} = useAsyncLoader(async (isCurrent) => {
  const loaded = await getSigningField(fieldId.value)
  if (isCurrent()) field.value = loaded
})

const title = computed(() => field.value?.documentTitle ?? t('pages.station-signing.title'))

const {running: preparing, failure: prepareFailure, run: prepare} = useAsyncAction(async () => {
  await act.prepare()
  announcement.value = t('signing.announce.ready')
  await nextTick()
  proofStep.value?.focusHeading()
})

const confirming = ref(false)
const confirmFailure = ref<Failure | null>(null)

/**
 * Runs one confirmation, keeping its failure in the signer's terms. Nothing runs twice at once: a second
 * press while the first is on its way is dropped, as it would sign nothing more.
 */
async function confirm(action: () => Promise<void>, waiting: string) {
  if (confirming.value) return
  confirming.value = true
  confirmFailure.value = null
  announcement.value = waiting
  try {
    await action()
    announcement.value = t('signing.announce.signed')
  } catch (e) {
    announcement.value = ''
    confirmFailure.value = describeSigningFailure(e, t)
  } finally {
    confirming.value = false
  }
}

function withAuthenticator() {
  void confirm(act.confirmWithAuthenticator, t('signing.announce.authenticator'))
}

function withSecret(proof: TypedProof, secret: string) {
  void confirm(() => act.confirmWithSecret(proof, secret), t('signing.announce.checking'))
}

watch(confirmed, (ticked) => {
  if (!ticked) confirmFailure.value = null
})
</script>

<template>
  <ViewContent :title="title" :subtitle="t('pages.station-signing.subtitle')">
    <p class="sr-only" role="status" aria-live="polite" data-testid="signing-announcement">{{ announcement }}</p>
    <Spinner v-if="loading"/>
    <SigningUnavailable v-else-if="loadFailure" :failure="loadFailure"/>
    <SigningDone v-else-if="act.outcome.value" :outcome="act.outcome.value"/>
    <div v-else-if="field" class="space-y-8 max-w-3xl">
      <SigningDocument :field-id="fieldId" :title="title" :file-name="`${title}.pdf`" @loaded="documentReady = true"/>
      <SigningStatement
          v-model:confirmed="confirmed"
          :field="field"
          :preparing="preparing"
          :failure="prepareFailure"
          :started="act.offer.value !== null"
          :document-ready="documentReady"
          @proceed="prepare"
      />
      <SigningProof
          v-if="confirmed && act.offer.value"
          ref="proofStep"
          :offer="act.offer.value"
          :busy="confirming"
          :failure="confirmFailure"
          @authenticator="withAuthenticator"
          @secret="withSecret"
      />
    </div>
  </ViewContent>
</template>
