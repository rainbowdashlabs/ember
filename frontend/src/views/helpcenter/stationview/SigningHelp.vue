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
import SigningStatement from '@/views/stationview/signing/signingview/SigningStatement.vue'
import SigningProof from '@/views/stationview/signing/signingview/SigningProof.vue'
import {FieldRole, SignerCapacity, StepUpProof} from '@/api/generated/schema'
import type {OpenSignatureResponse, SigningStartResponse} from '@/api/generated/schema'

const {t} = useI18n()

const exampleConfirmed = ref(true)

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

const exampleOffer = computed<SigningStartResponse>(() => ({
  startToken: '',
  expiresAt: '2026-10-08T12:05:00Z',
  fieldId: 0,
  requestUid: '00000000-0000-0000-0000-000000000000',
  fieldName: 'guardian1',
  role: FieldRole.GUARDIAN,
  capacity: SignerCapacity.GUARDIAN,
  statement: t('helpCenter.signing.exampleStatement'),
  signerName: t('helpCenter.signing.exampleGuardian'),
  accountHolderName: t('helpCenter.signing.exampleGuardian'),
  memberName: t('helpCenter.signing.exampleChild'),
  documentMemberName: t('helpCenter.signing.exampleChild'),
  contentSha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
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
      <p>{{ t('helpCenter.signing.stepRead') }}</p>
      <p>{{ t('helpCenter.signing.stepConfirm') }}</p>
      <p>{{ t('helpCenter.signing.stepProve') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-8">
      <SigningStatement v-model:confirmed="exampleConfirmed" :field="exampleField" :preparing="false" :failure="null" started document-ready/>
      <SigningProof :offer="exampleOffer" :busy="false" :failure="null"/>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.signing.proofs')">
      <p>{{ t('helpCenter.signing.proofsBound') }}</p>
      <p>{{ t('helpCenter.signing.proofsUnbound') }}</p>
      <p>{{ t('helpCenter.signing.proofsNever') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.signing.forOthers')">
      <p>{{ t('helpCenter.signing.forOthersGuardian') }}</p>
      <p>{{ t('helpCenter.signing.forOthersThrough') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.signing.access')">
      <p>{{ t('helpCenter.signing.accessText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.signing.tip') }}</HelpTip>
  </HelpArticle>
</template>
