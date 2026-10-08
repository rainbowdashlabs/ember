/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SealedVersionList from '@/components/documents/SealedVersionList.vue'
import SignatureStateBadge from '@/components/documents/SignatureStateBadge.vue'
import {RequestState, SealLevel, type SealedVersionResponse, type SignatureSummary} from '@/api/generated/schema'

/**
 * The station's store and the documents of single members: who sees what, generating, searching, keeping,
 * pruning, and what a sealed document with signatures shows.
 */
const {t} = useI18n()

const partlySigned: SignatureSummary = {
  requestUid: '00000000-0000-0000-0000-000000000000',
  state: RequestState.OPEN,
  signed: 1,
  expected: 2,
  open: 1,
  nobodyCanSign: 0,
}

const versions: SealedVersionResponse[] = [
  {
    version: 2,
    sha256: '3f8a1c9e5b7d2f4a6c8e0b1d3f5a7c9e1b3d5f7a9c1e3b5d7f9a1c3e5b7d9f1a',
    sizeBytes: 184320,
    sealLevel: SealLevel.BASELINE_LT,
    timestampedBy: 'http://timestamp.digicert.com',
    sealedAt: '2026-10-07T17:42:00Z',
    supersededAt: null,
  },
  {
    version: 1,
    sha256: '9d2b4f6a8c0e2a4c6e8a0c2e4a6c8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c',
    sizeBytes: 176128,
    sealLevel: SealLevel.BASELINE_LT,
    timestampedBy: 'http://timestamp.digicert.com',
    sealedAt: '2026-10-05T08:15:00Z',
    supersededAt: '2026-10-07T17:42:00Z',
  },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.documents.title')" :subtitle="t('helpCenter.documents.subtitle')">
    <HelpSection :title="t('helpCenter.documents.whatIs')">
      <p>{{ t('helpCenter.documents.whatIsText') }}</p>
      <p>{{ t('helpCenter.documents.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.rightsTitle')">
      <p>{{ t('helpCenter.documents.rightsRead') }}</p>
      <p>{{ t('helpCenter.documents.rightsSelf') }}</p>
      <p>{{ t('helpCenter.documents.rightsOthers') }}</p>
      <p>{{ t('helpCenter.documents.rightsOwn') }}</p>
      <p>{{ t('helpCenter.documents.rightsBound') }}</p>
      <p>{{ t('helpCenter.documents.rightsHidden') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.generateTitle')">
      <p>{{ t('helpCenter.documents.generateText') }}</p>
      <p>{{ t('helpCenter.documents.generateRightsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.bulkTitle')">
      <p>{{ t('helpCenter.documents.bulkText') }}</p>
      <p>{{ t('helpCenter.documents.bulkPreviewText') }}</p>
      <p>{{ t('helpCenter.documents.bulkRunText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.unboundTitle')">
      <p>{{ t('helpCenter.documents.unboundText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.moduleTitle')">
      <p>{{ t('helpCenter.documents.moduleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.previewTitle')">
      <p>{{ t('helpCenter.documents.previewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.searchTitle')">
      <p>{{ t('helpCenter.documents.searchText') }}</p>
      <p>{{ t('helpCenter.documents.tagsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.keepTitle')">
      <p>{{ t('helpCenter.documents.keepText') }}</p>
      <p>{{ t('helpCenter.documents.deletedText') }}</p>
      <p>{{ t('helpCenter.documents.exportText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.pruneTitle')">
      <p>{{ t('helpCenter.documents.pruneText') }}</p>
      <p>{{ t('helpCenter.documents.pruneText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.sealedTitle')">
      <p>{{ t('helpCenter.documents.sealedText') }}</p>
      <p>{{ t('helpCenter.documents.sealedStateText') }}</p>
      <p>{{ t('helpCenter.documents.sealedVersionsText') }}</p>
      <p>{{ t('helpCenter.documents.sealedManageText') }}</p>
      <NeutralContainer class="space-y-3">
        <SignatureStateBadge :summary="partlySigned"/>
        <SealedVersionList :versions="versions" file-name="einverstaendnis.pdf"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.filingRulesTitle')">
      <p>{{ t('helpCenter.documents.filingRulesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.documents.tip') }}</HelpTip>
  </HelpArticle>
</template>
