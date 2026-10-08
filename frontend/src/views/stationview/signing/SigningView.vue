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
import SigningFillIns from './signingview/SigningFillIns.vue'
import {fillInEntries, fillInsComplete, type FillInValues} from './signingview/fillIns'
import SigningProof from './signingview/SigningProof.vue'
import SigningMark from './signingview/SigningMark.vue'
import SigningDone from './signingview/SigningDone.vue'
import {useSigningAct, type SigningMarkChoice, type TypedProof} from './signingview/useSigningAct'
import {describeSigningFailure} from './signingview/signingFailure'
import {SignerCapacity} from '@/api/generated/schema'
import type {FillInResponse, OpenSignatureResponse} from '@/api/generated/schema'
import {getSigningField, getSigningFillIns} from '@/api/signing'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useOwnSignature} from '@/composables/useOwnSignature'
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
 * <p>The proof step also takes the signature picture the act leaves in its field: the signer's saved one,
 * or one drawn or typed there. The saved picture is read along with the field; where it cannot be read
 * the signer simply draws one, and the proof waits until there is a picture.
 *
 * <p>Where the document asks the signer to fill in fields of their own, such as a phone number, their
 * inputs stand in the statement step and are read with the field. The values go with the start of the act,
 * which binds them, so they are locked once it is started.
 *
 * <p>The address is what a "please sign" link opens, so the page loads the field itself and says plainly
 * when it no longer waits for the reader, was never theirs, or is gone.
 */
const {t} = useI18n()
const route = useRoute()
const fieldId = computed(() => Number(route.params.fieldId))

const field = ref<OpenSignatureResponse | null>(null)
const fillIns = ref<FillInResponse[]>([])
const fillInValues = ref<FillInValues>({})
const confirmed = ref(false)
const documentReady = ref(false)
const announcement = ref('')
const proofStep = ref<InstanceType<typeof SigningProof> | null>(null)

const act = useSigningAct(() => fieldId.value, () => fillInEntries(fillIns.value, fillInValues.value))
const {imageUrl: savedSignature, loadImage: loadSavedSignature} = useOwnSignature()
const mark = ref<SigningMarkChoice>({draft: null, useSaved: true, keep: false})

const {loading, failure: loadFailure} = useAsyncLoader(async (isCurrent) => {
  const [loaded, asked] = await Promise.all([getSigningField(fieldId.value), getSigningFillIns(fieldId.value)])
  if (!isCurrent()) return
  field.value = loaded
  fillIns.value = asked
  await loadSavedSignature().catch(() => undefined)
})

const filledIn = computed(() => fillInsComplete(fillIns.value, fillInValues.value))

/**
 * Whether the act has a picture to leave in its field: the saved one, where the signer may use it and
 * chose to, or one made here. A member signing through another's account always makes their own.
 */
const markReady = computed(() => {
  const capacity = act.offer.value?.capacity ?? field.value?.capacity
  const savedUsable = capacity !== SignerCapacity.MEMBER_THROUGH_ACCOUNT && savedSignature.value !== null
  return mark.value.draft !== null || (savedUsable && mark.value.useSaved)
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
  void confirm(() => act.confirmWithAuthenticator(mark.value), t('signing.announce.authenticator'))
}

function withSecret(proof: TypedProof, secret: string) {
  void confirm(() => act.confirmWithSecret(proof, secret, mark.value), t('signing.announce.checking'))
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
          :filled-in="filledIn"
          @proceed="prepare"
      >
        <SigningFillIns
            v-if="fillIns.length > 0"
            v-model="fillInValues"
            :fields="fillIns"
            :locked="act.offer.value !== null"
        />
      </SigningStatement>
      <SigningProof
          v-if="confirmed && act.offer.value"
          ref="proofStep"
          :offer="act.offer.value"
          :busy="confirming || !markReady"
          :failure="confirmFailure"
          @authenticator="withAuthenticator"
          @secret="withSecret"
      >
        <SigningMark
            v-model="mark"
            :capacity="act.offer.value.capacity"
            :member-name="act.offer.value.memberName"
            :saved-url="savedSignature"
        />
      </SigningProof>
    </div>
  </ViewContent>
</template>
