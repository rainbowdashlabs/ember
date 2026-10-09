/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SigningUnavailable from './signingview/SigningUnavailable.vue'
import SigningNothingOpen from './signingview/SigningNothingOpen.vue'
import SigningOverviewStep from './signingview/SigningOverviewStep.vue'
import SigningDocumentStep from './signingview/SigningDocumentStep.vue'
import SigningHolderPictureStep from './signingview/SigningHolderPictureStep.vue'
import SigningMemberPictureStep from './signingview/SigningMemberPictureStep.vue'
import SigningCheckStep from './signingview/SigningCheckStep.vue'
import SigningConfirmStep from './signingview/SigningConfirmStep.vue'
import SigningDone from './signingview/SigningDone.vue'
import {holderName, namedFields} from './signingview/batchFlow'
import {useSession} from '@/composables/useSession'
import {useSigningFlow} from './signingview/useSigningFlow'
import type {TypedProof} from './signingview/useBatchSigning'
import {describeSigningFailure} from './signingview/signingFailure'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useOwnSignature} from '@/composables/useOwnSignature'
import type {Failure} from '@/util/failure'
import type {SignatureDraft} from '@/util/signatureDraft'

/**
 * Signing in one go: every field the reader may sign now, across documents and the members in their care,
 * in small steps with one thing each. The overview ticks what to sign, each document is read and agreed
 * to on its own screen, the signature picture comes once (and once more for each child who signs in
 * person), a check lists it all again, and one confirmation signs everything.
 *
 * <p>Opened from a field ("please sign" link, an appointment), that field's document comes first; opened
 * with `fields=1,2,3` only those are ticked. Every screen takes the focus on its heading, and the live
 * region says which step the reader is on. Nothing of a later step is shown before it is reached, and
 * going back keeps everything ticked and typed.
 */
const {t} = useI18n()
const route = useRoute()

const firstFieldId = computed(() => (route.params.fieldId ? Number(route.params.fieldId) : null))
const flow = useSigningFlow(() => firstFieldId.value, () => namedFields(route.query.fields))
const {imageUrl: savedSignature, loadImage: loadSavedSignature} = useOwnSignature()

const {loading, failure: loadFailure} = useAsyncLoader(async (isCurrent) => {
  await flow.load()
  if (isCurrent()) await loadSavedSignature().catch(() => undefined)
})

const notWaiting = computed(() => firstFieldId.value !== null
    && !flow.fields.value.some(field => field.fieldId === firstFieldId.value))
const total = computed(() => flow.steps.value.length)
const {fullName} = useSession()
const holder = computed(() => holderName(flow.selected.value.flatMap(group => group.fields)) ?? fullName())
const announcement = ref('')

const {running: preparing, failure: prepareFailure, run: prepare} = useAsyncAction(() => flow.act.prepare())

watch(() => `${flow.position.value}:${flow.step.value.kind}`, () => {
  const kind = flow.step.value.kind
  announcement.value = t('signing.flow.announce', {
    position: flow.position.value,
    total: total.value,
    step: t(`signing.flow.stepName.${kind}`),
  })
  if (kind === 'confirm') void prepare()
})

const confirming = ref(false)
const confirmFailure = ref<Failure | null>(null)

/** Runs one confirmation; a second press while the first is on its way is dropped. */
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
  void confirm(() => flow.act.confirmWithAuthenticator(flow.pictures()), t('signing.announce.authenticator'))
}

function withSecret(proof: TypedProof, secret: string) {
  void confirm(() => flow.act.confirmWithSecret(proof, secret, flow.pictures()), t('signing.announce.checking'))
}

const step = flow.step

/** Keeps what the member on the current picture step drew. */
function drawMember(draft: SignatureDraft | null) {
  const current = step.value
  if (current.kind === 'memberPicture') flow.setMemberMark(current.signer.memberId, draft)
}
const title = computed(() => (firstFieldId.value === null ? t('pages.station-signing-all.title') : t('pages.station-signing.title')))
</script>

<template>
  <ViewContent :title="title" :subtitle="t('pages.station-signing.subtitle')">
    <p class="sr-only" role="status" aria-live="polite" data-testid="signing-announcement">{{ announcement }}</p>
    <Spinner v-if="loading"/>
    <SigningUnavailable v-else-if="loadFailure" :failure="loadFailure"/>
    <SigningDone v-else-if="flow.act.outcome.value" :signed="flow.act.outcome.value.fields.length"/>
    <SigningNothingOpen v-else-if="flow.fields.value.length === 0"/>
    <div v-else class="space-y-4">
      <Alert v-if="notWaiting && step.kind === 'overview'" variant="info">{{ t('signing.flow.notWaiting') }}</Alert>
      <SigningOverviewStep v-if="step.kind === 'overview'" v-model="flow.chosen.value" :groups="flow.groups.value"
                           :position="flow.position.value" :total="total" @next="flow.next"/>
      <SigningDocumentStep v-else-if="step.kind === 'document'" :key="step.group.requestUid"
                           v-model:agreed="flow.agreed.value" v-model:values="flow.values.value" :group="step.group"
                           :index="step.index" :count="step.count" :fill-ins="flow.fillIns.value"
                           :position="flow.position.value" :total="total" @next="flow.next" @back="flow.back"/>
      <SigningHolderPictureStep v-else-if="step.kind === 'holderPicture'" v-model="flow.holderMark.value"
                                :saved-url="savedSignature" :signer="holder" :position="flow.position.value" :total="total"
                                @next="flow.next" @back="flow.back"/>
      <SigningMemberPictureStep v-else-if="step.kind === 'memberPicture'" :key="step.signer.memberId"
                                :model-value="flow.memberMarks.value[step.signer.memberId] ?? null"
                                :signer="step.signer" :position="flow.position.value" :total="total"
                                @update:model-value="drawMember"
                                @next="flow.next" @back="flow.back"/>
      <SigningCheckStep v-else-if="step.kind === 'check'" :groups="flow.selected.value"
                        :position="flow.position.value" :total="total" @next="flow.next" @back="flow.back"/>
      <SigningConfirmStep v-else :offer="flow.act.offer.value" :preparing="preparing" :busy="confirming"
                          :failure="confirmFailure ?? prepareFailure" :position="flow.position.value" :total="total"
                          @authenticator="withAuthenticator" @secret="withSecret" @back="flow.back"/>
    </div>
  </ViewContent>
</template>
