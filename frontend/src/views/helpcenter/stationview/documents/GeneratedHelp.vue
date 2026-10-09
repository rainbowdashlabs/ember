/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SignatureStateBadge from '@/components/documents/SignatureStateBadge.vue'
import SignatureFieldRow from '@/components/documents/signatures/SignatureFieldRow.vue'
import {
    FieldRole,
    FieldState,
    RequestState,
    SignerCapacity,
    StationPermission,
    StepUpProof,
    type ManagedFieldResponse,
    type SignatureSummary,
} from '@/api/generated/schema'

/**
 * The list of documents generated from templates: what it says about each document, how to narrow it, how
 * the signatures asked for on a document stand, and how a manager looks after them.
 */
const {t} = useI18n()

const REQUEST_UID = '00000000-0000-0000-0000-000000000000'

const summaries: SignatureSummary[] = [
    {requestUid: REQUEST_UID, state: RequestState.COMPLETE, signed: 2, expected: 2, open: 0, nobodyCanSign: 0},
    {requestUid: REQUEST_UID, state: RequestState.OPEN, signed: 1, expected: 3, open: 2, nobodyCanSign: 1},
    {requestUid: REQUEST_UID, state: RequestState.WITHDRAWN, signed: 0, expected: 0, open: 0, nobodyCanSign: 0},
]

const fields: ManagedFieldResponse[] = [
    {
        fieldName: 'guardian1',
        role: FieldRole.GUARDIAN,
        signerName: 'Sabine Hoffmann',
        capacity: SignerCapacity.GUARDIAN,
        statement: t('helpCenter.generatedDocuments.exampleStatement'),
        state: FieldState.SIGNED,
        nobodyCanSign: false,
        settledAt: '2026-10-07T17:42:00Z',
        settledByName: 'Sabine Hoffmann',
        act: {
            signerName: 'Sabine Hoffmann',
            accountHolderName: 'Sabine Hoffmann',
            capacity: SignerCapacity.GUARDIAN,
            proof: StepUpProof.PASSKEY,
            bound: true,
            userVerified: true,
            signedAt: '2026-10-07T17:42:00Z',
            sealed: true,
        },
    },
    {
        fieldName: 'guardian2',
        role: FieldRole.GUARDIAN,
        signerName: null,
        capacity: SignerCapacity.GUARDIAN,
        statement: t('helpCenter.generatedDocuments.exampleStatement'),
        state: FieldState.OPEN,
        nobodyCanSign: true,
        settledAt: null,
        settledByName: null,
        act: null,
    },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.generatedDocuments.title')" :subtitle="t('helpCenter.generatedDocuments.subtitle')">
    <HelpSection :title="t('helpCenter.generatedDocuments.whatIs')">
      <p>{{ t('helpCenter.generatedDocuments.whatIsText') }}</p>
      <p>{{ t('helpCenter.generatedDocuments.versionText') }}</p>
      <p>{{ t('helpCenter.generatedDocuments.rightsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.generatedDocuments.howTo')">
      <p>{{ t('helpCenter.generatedDocuments.howToText') }}</p>
      <p>{{ t('helpCenter.generatedDocuments.goneText') }}</p>
      <p>{{ t('helpCenter.generatedDocuments.pagesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.generatedDocuments.signatureTitle')">
      <p>{{ t('helpCenter.generatedDocuments.signatureText') }}</p>
      <p>{{ t('helpCenter.generatedDocuments.signatureNobodyText') }}</p>
      <NeutralContainer class="flex flex-col items-start gap-2">
        <SignatureStateBadge v-for="summary in summaries" :key="summary.state" :summary="summary"/>
      </NeutralContainer>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.DOCUMENT_EDIT_MEMBER]"
                         :label="t('helpCenter.permissionLabel.documentEditMember')">
      <HelpSection :title="t('helpCenter.generatedDocuments.manageTitle')">
        <p>{{ t('helpCenter.generatedDocuments.manageText') }}</p>
        <p>{{ t('helpCenter.generatedDocuments.manageSettleText') }}</p>
        <p>{{ t('helpCenter.generatedDocuments.manageWithdrawText') }}</p>
        <p>{{ t('helpCenter.generatedDocuments.manageCorrectionText') }}</p>
        <NeutralContainer>
          <ul class="space-y-3">
            <SignatureFieldRow v-for="field in fields" :key="field.fieldName" :field="field" settleable :busy="false"/>
          </ul>
        </NeutralContainer>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.generatedDocuments.tip') }}</HelpTip>
  </HelpArticle>
</template>
