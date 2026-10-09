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
import FileUploadField from '@/components/input/FileUploadField.vue'
import BulletList from '@/components/typography/BulletList.vue'
import {
  PadesLevel,
  RevocationStatus,
  SealLevel,
  ValidationIndication,
  ValidationSubIndication,
  type HeldCopy,
  type SealCheck,
} from '@/api/generated/schema'
import {SEAL_CHECK_MAX_BYTES} from '@/api/signing'
import HeldCopyNotice from '@/views/public/sealverificationview/HeldCopyNotice.vue'
import SealCheckCard from '@/views/public/sealverificationview/SealCheckCard.vue'

const {t} = useI18n()

const STATION_CA = {
  subject: 'CN=Ember signing authority ember.example.org,O=ember.example.org',
  serialNumber: '5c1e0a7f3b9d2e41',
  sha256Fingerprint: '3A:7F:12:C4:9B:E0:55:D1:08:6A:F3:2C:91:BE:47:0D:'
    + 'E2:19:84:5B:C7:3F:A0:6E:D8:21:9C:44:F5:0B:7A:13',
}

const TIMESTAMP = {
  time: '2026-10-05T09:12:04Z',
  authority: {
    subject: 'CN=DigiCert SHA256 RSA4096 Timestamp Responder 2025 1,O=DigiCert\\, Inc.,C=US',
    serialNumber: '0a6b2f4e',
    sha256Fingerprint: '9C:2D:51:7E:A3:08:F6:4B:1D:C0:3A:92:E7:5F:18:6C:'
      + '04:BB:D9:21:7A:E5:30:C8:6F:12:9D:A4:57:0E:B3:61',
  },
  pinnedAuthority: true,
  indication: ValidationIndication.PASSED,
  subIndication: null,
  intact: true,
}

/** A seal as the page shows it, with only what tells the two examples apart left to fill in. */
function exampleSeal(changes: Partial<SealCheck>): SealCheck {
  return {
    signer: {
      subject: 'CN=Jugendfeuerwehr Musterstadt,UID=0b6f6a52-3a4e-4d1c-9a27-5f8e2c1d7b90,O=ember.example.org',
      serialNumber: '7d20c1e94a',
      sha256Fingerprint: 'B1:0E:6C:2A:94:F7:13:5D:E8:42:7B:C9:06:3F:A1:D4:'
        + '58:2E:9B:70:C3:1A:E6:84:2F:D5:0C:B7:69:13:4E:A8',
    },
    issuer: STATION_CA,
    issuedHere: true,
    partner: null,
    indication: ValidationIndication.TOTAL_PASSED,
    subIndication: null,
    validatorIndication: ValidationIndication.TOTAL_PASSED,
    validatorSubIndication: null,
    level: PadesLevel.BASELINE_LT,
    signingTime: '2026-10-05T09:12:03Z',
    intact: true,
    coversWholeFile: false,
    modifiedAfterSealing: false,
    revocation: {status: RevocationStatus.GOOD, revokedAt: null, reason: null},
    timestamps: [TIMESTAMP],
    ...changes,
  }
}

const SEALED_HERE = exampleSeal({})
const ALTERED = exampleSeal({
  indication: ValidationIndication.TOTAL_FAILED,
  subIndication: ValidationSubIndication.HASH_FAILURE,
  validatorIndication: ValidationIndication.TOTAL_FAILED,
  validatorSubIndication: ValidationSubIndication.HASH_FAILURE,
  intact: false,
})

const HELD: HeldCopy = {held: true, sealedAt: '2026-10-05T09:12:03Z', sealLevel: SealLevel.BASELINE_LT}
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.seals.title')" :subtitle="t('helpCenter.basics.seals.subtitle')">
    <HelpSection :title="t('helpCenter.basics.seals.whatIs')">
      <p>{{ t('helpCenter.basics.seals.whatIsText') }}</p>
      <p>{{ t('helpCenter.basics.seals.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.howTo')">
      <p>{{ t('helpCenter.basics.seals.step1') }}</p>
      <NuxtLink to="/verify" class="text-(--link) hover:underline">{{ t('helpCenter.basics.seals.openPage') }}</NuxtLink>
      <p>{{ t('helpCenter.basics.seals.step2') }}</p>
      <p>{{ t('helpCenter.basics.seals.step3') }}</p>
      <NeutralContainer class="mt-3">
        <FileUploadField :max-size="SEAL_CHECK_MAX_BYTES" :label="t('sealVerification.choose')" disabled droppable/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.resultsTitle')">
      <BulletList>
        <li>{{ t('helpCenter.basics.seals.resultSealedHere') }}</li>
        <li>{{ t('helpCenter.basics.seals.resultAltered') }}</li>
        <li>{{ t('helpCenter.basics.seals.resultModifiedAfterSealing') }}</li>
        <li>{{ t('helpCenter.basics.seals.resultNotIssuedHere') }}</li>
        <li>{{ t('helpCenter.basics.seals.resultInvalid') }}</li>
        <li>{{ t('helpCenter.basics.seals.resultUnclear') }}</li>
        <li>{{ t('helpCenter.basics.seals.resultNone') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.exampleTitle')">
      <div class="space-y-3">
        <SealCheckCard :check="SEALED_HERE"/>
        <SealCheckCard :check="ALTERED"/>
        <HeldCopyNotice :document="HELD"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.revokedTitle')">
      <p>{{ t('helpCenter.basics.seals.revokedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.heldTitle')">
      <p>{{ t('helpCenter.basics.seals.heldText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.evidenceTitle')">
      <p>{{ t('helpCenter.basics.seals.evidenceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.compareTitle')">
      <p>{{ t('helpCenter.basics.seals.compareText') }}</p>
      <p>{{ t('helpCenter.basics.seals.compareText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.offlineTitle')">
      <p>{{ t('helpCenter.basics.seals.offlineText') }}</p>
      <p>{{ t('helpCenter.basics.seals.offlineStep1') }}</p>
      <p>{{ t('helpCenter.basics.seals.offlineStep2') }}</p>
      <p>{{ t('helpCenter.basics.seals.offlineStep3') }}</p>
      <p>{{ t('helpCenter.basics.seals.offlineText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.seals.limitsTitle')">
      <p>{{ t('helpCenter.basics.seals.limitsText') }}</p>
      <p>{{ t('helpCenter.basics.seals.limitsText2') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.seals.tip') }}</HelpTip>
  </HelpArticle>
</template>
