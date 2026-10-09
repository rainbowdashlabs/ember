/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SigningFieldAgreement from '@/views/stationview/signing/signingview/SigningFieldAgreement.vue'
import SigningProof from '@/views/stationview/signing/signingview/SigningProof.vue'
import type {FillInValues} from '@/views/stationview/signing/signingview/fillIns'
import {FieldRole, SignerCapacity, StepUpProof} from '@/api/generated/schema'
import type {BatchStartResponse, FillInResponse, OpenSignatureResponse} from '@/api/generated/schema'

const {t} = useI18n()

const exampleAgreed = ref(true)
const exampleFillIns = computed<FillInResponse[]>(() => [
  {name: 'fill-guardian1-0', label: t('helpCenter.signing.exampleFillIn'), required: true, maxLength: 40},
])
const exampleValues = ref<FillInValues>({'fill-guardian1-0': '0171 2345678'})

const exampleField = computed<OpenSignatureResponse>(() => ({
  fieldId: 0,
  requestUid: '00000000-0000-0000-0000-000000000000',
  documentId: 0,
  documentTitle: t('helpCenter.signing.exampleTitle'),
  memberName: t('helpCenter.signing.exampleChild'),
  fieldName: 'guardian1',
  role: FieldRole.GUARDIAN,
  capacity: SignerCapacity.GUARDIAN,
  memberId: 0,
  signerName: t('helpCenter.signing.exampleGuardian'),
  statement: t('helpCenter.signing.exampleStatement'),
}))

const exampleOffer = computed<BatchStartResponse>(() => ({
  startToken: '',
  expiresAt: '2026-10-08T12:05:00Z',
  batchUid: '00000000-0000-0000-0000-000000000000',
  fields: [],
  acceptedProofs: [StepUpProof.PASSKEY, StepUpProof.TOTP],
  webAuthnOptionsJson: null,
}))
</script>

<template>
  <HelpArticle :title="t('helpCenter.signing.title')" :subtitle="t('helpCenter.signing.subtitle')">
    <HelpSection :title="t('helpCenter.signing.whatIs')">
      <p>{{ t('helpCenter.signing.whatIsText') }}</p>
      <p>{{ t('helpCenter.signing.reachText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.signing.steps')">
      <p>{{ t('helpCenter.signing.stepOverview') }}</p>
      <p>{{ t('helpCenter.signing.stepDocument') }}</p>
      <p>{{ t('helpCenter.signing.stepPicture') }}</p>
      <p>{{ t('helpCenter.signing.stepCheck') }}</p>
      <p>{{ t('helpCenter.signing.stepConfirm') }}</p>
      <p>{{ t('helpCenter.signing.stepDone') }}</p>
      <p>{{ t('helpCenter.signing.stepBack') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-6">
      <SigningFieldAgreement v-model:agreed="exampleAgreed" v-model:values="exampleValues" :field="exampleField"
                             :fill-ins="exampleFillIns"/>
      <SigningProof :offer="exampleOffer" :busy="false"/>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.signing.proofs')">
      <p>{{ t('helpCenter.signing.proofsBound') }}</p>
      <p>{{ t('helpCenter.signing.proofsUnbound') }}</p>
      <p>{{ t('helpCenter.signing.proofsNever') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.signing.forOthers')">
      <p>{{ t('helpCenter.signing.forOthersGuardian') }}</p>
      <p>{{ t('helpCenter.signing.forOthersThrough') }}</p>
      <p>{{ t('helpCenter.signing.forOthersMark') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.signing.mail')">
      <p>{{ t('helpCenter.signing.mailReminder') }}</p>
      <p>{{ t('helpCenter.signing.mailCopy') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.signing.withdraw')">
      <p>{{ t('helpCenter.signing.withdrawText') }}</p>
      <p>{{ t('helpCenter.signing.withdrawRecord') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.signing.access')">
      <p>{{ t('helpCenter.signing.accessText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.signing.tip') }}</HelpTip>
  </HelpArticle>
</template>
