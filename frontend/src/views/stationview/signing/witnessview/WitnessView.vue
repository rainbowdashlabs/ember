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
import SigningUnavailable from '../signingview/SigningUnavailable.vue'
import WitnessPersonStep from './WitnessPersonStep.vue'
import WitnessDocumentStep from './WitnessDocumentStep.vue'
import WitnessSignerStep from './WitnessSignerStep.vue'
import WitnessHandedOverSteps from './WitnessHandedOverSteps.vue'
import WitnessConfirmStep from './WitnessConfirmStep.vue'
import WitnessDone from './WitnessDone.vue'
import type {WitnessPersonResponse} from '@/api/generated/schema'
import {getWitnessPeople} from '@/api/witnessSigning'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeSigningFailure} from '../signingview/signingFailure'
import type {TypedProof} from '../signingview/useBatchSigning'
import type {Failure} from '@/util/failure'
import {useWitnessFlow} from './useWitnessFlow'
import {handedOver, positionOf, WITNESS_STEPS} from './witnessFlow'

/**
 * Signatures collected in person: the witness picks a person and what they sign, hands the device over,
 * the person reads, agrees and draws, hands it back, and the witness confirms with their own account.
 *
 * <p>Opened with `field=…` from a document's tile, that field is the one collected, for the person its
 * document is about. While the person holds the device, nothing of the witness's own is reachable
 * ({@link WitnessHandedOverSteps}). The live region says which step is shown.
 */
const {t} = useI18n()
const route = useRoute()
const {fullName} = useSession()
const flow = useWitnessFlow()
const people = ref<WitnessPersonResponse[]>([])

const {loading, failure: loadFailure} = useAsyncLoader(async (isCurrent) => {
  const fieldId = Number(route.query.field)
  if (Number.isInteger(fieldId) && fieldId > 0) await flow.openField(fieldId)
  const loaded = await getWitnessPeople()
  if (isCurrent()) people.value = loaded
})

const step = flow.step
const position = computed(() => positionOf(step.value))
const total = WITNESS_STEPS.length
const signerName = computed(() => flow.signer.value?.name ?? '')
const announcement = ref('')

const {running: choosing, failure: chooseFailure, run: choosingRun} = useAsyncAction(
    (action: () => Promise<void>) => action())
const {running: preparing, failure: prepareFailure, run: prepare} = useAsyncAction(() => flow.act.prepare())

watch(step, current => {
  announcement.value = t('signing.flow.announce', {
    position: positionOf(current),
    total,
    step: t(`signing.witness.stepName.${current}`),
  })
  if (current === 'confirm') void prepare()
})

const confirming = ref(false)
const confirmFailure = ref<Failure | null>(null)

/** Runs the witness's confirmation; a second press while the first is on its way is dropped. */
async function confirm(action: () => Promise<void>) {
  if (confirming.value) return
  confirming.value = true
  confirmFailure.value = null
  try {
    await action()
    announcement.value = t('signing.announce.signed')
  } catch (e) {
    confirmFailure.value = describeSigningFailure(e, t)
  } finally {
    confirming.value = false
  }
}

function withAuthenticator() {
  void confirm(() => flow.act.confirmWithAuthenticator(flow.pictures()))
}

function withSecret(proof: TypedProof, secret: string) {
  void confirm(() => flow.act.confirmWithSecret(proof, secret, flow.pictures()))
}
</script>

<template>
  <ViewContent :title="t('pages.station-signing-witness.title')" :subtitle="t('pages.station-signing-witness.subtitle')">
    <p class="sr-only" role="status" aria-live="polite" data-testid="witness-announcement">{{ announcement }}</p>
    <Spinner v-if="loading"/>
    <SigningUnavailable v-else-if="loadFailure" :failure="loadFailure"/>
    <WitnessDone v-else-if="flow.act.outcome.value" :name="signerName"
                 @next-person="flow.nextPerson" @same-person="choosingRun(flow.samePerson)"/>
    <WitnessPersonStep v-else-if="step === 'person'" :people="people" :position="position" :total="total"
                       @pick="person => choosingRun(() => flow.pickPerson(person))"/>
    <WitnessDocumentStep v-else-if="step === 'document' && flow.person.value && flow.offers.value"
                         :person="flow.person.value" :offers="flow.offers.value" :busy="choosing" :failure="chooseFailure"
                         :position="position" :total="total" @choose="field => choosingRun(() => flow.choose(field))"
                         @generate="id => choosingRun(() => flow.generate(id))" @back="flow.nextPerson"/>
    <WitnessSignerStep v-else-if="step === 'signer' && flow.field.value" :field="flow.field.value" :position="position"
                       :total="total" @pick="flow.pickSigner" @back="flow.go('document')"/>
    <WitnessHandedOverSteps v-else-if="handedOver(step) && flow.field.value" v-model:agreed="flow.agreed.value"
                            v-model:values="flow.values.value" v-model:draft="flow.draft.value" :step="step"
                            :field="flow.field.value" :name="signerName" :witness="fullName()" :fill-ins="flow.fillIns.value"
                            :ready="flow.readReady.value" :abandoned="flow.abandoned.value"
                            @go="flow.go" @give-back="flow.abandon" @taken-back="flow.takenBack"/>
    <WitnessConfirmStep v-else-if="step === 'confirm'" :name="signerName" :offer="flow.act.offer.value" :preparing="preparing"
                        :busy="confirming" :failure="confirmFailure ?? prepareFailure" :position="position" :total="total"
                        @authenticator="withAuthenticator" @secret="withSecret" @back="flow.go('handOver')"/>
  </ViewContent>
</template>
