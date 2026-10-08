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
import SigningKeyHealthPanel from '@/views/adminview/adminsecuritysigningkeysview/SigningKeyHealthPanel.vue'
import SigningKeyRecoveryHistory from '@/views/adminview/adminsecuritysigningkeysview/SigningKeyRecoveryHistory.vue'
import {SigningKeyKind} from '@/api/generated/schema'
import type {SigningKeyStatus} from '@/api/generated/schema'

const {t} = useI18n()

const lockedStatus: SigningKeyStatus = {
  locked: [
    {
      kind: SigningKeyKind.AUTHORITY,
      serialNumber: '5f1c0a7e93b24d6e8a01c2f4b7d9e305a6c8b1d2',
      sha256Fingerprint: '3A:91:0C:5E:77:D2:4B:18:9F:E0:21:6A:C4:58:B3:0D:E7:12:9A:44:5C:F8:01:B6:2D:73:E9:0A:C1:56:8B:F4',
      active: true,
      stationName: null,
      validUntil: '2046-03-14T09:00:00Z',
    },
    {
      kind: SigningKeyKind.STATION_KEY,
      serialNumber: '2b7e4c19d05a8f63e1c7b2a904d6f8e1c3a5b7d9',
      sha256Fingerprint: 'C7:04:E2:19:5B:A8:6F:33:D1:0E:97:4C:2A:B5:F6:81:0D:E3:7A:59:C2:14:8B:6E:F0:A3:27:D9:4E:15:B8:62',
      active: true,
      stationName: t('helpCenter.adminSecuritySigningKeys.sampleStation'),
      validUntil: '2031-03-14T09:00:00Z',
    },
  ],
  openKeys: 0,
  recoveries: [],
}

const recoveries: SigningKeyStatus['recoveries'] = [
  {
    id: 1,
    recoveredAt: '2026-05-02T18:20:00Z',
    recoveredBy: t('helpCenter.adminSecuritySigningKeys.sampleAdmin'),
    authoritySerials: ['5f1c0a7e93b24d6e8a01c2f4b7d9e305a6c8b1d2'],
    stationKeySerials: ['2b7e4c19d05a8f63e1c7b2a904d6f8e1c3a5b7d9'],
  },
]
</script>

<template>
  <HelpArticle
      :title="t('helpCenter.adminSecuritySigningKeys.title')"
      :subtitle="t('helpCenter.adminSecuritySigningKeys.subtitle')">
    <HelpSection :title="t('helpCenter.adminSecuritySigningKeys.whatIs')">
      <p>{{ t('helpCenter.adminSecuritySigningKeys.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecuritySigningKeys.lockedTitle')">
      <p>{{ t('helpCenter.adminSecuritySigningKeys.lockedText') }}</p>
      <SigningKeyHealthPanel :status="lockedStatus"/>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecuritySigningKeys.recoverTitle')">
      <p>{{ t('helpCenter.adminSecuritySigningKeys.recoverText') }}</p>
      <p>{{ t('helpCenter.adminSecuritySigningKeys.keepsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecuritySigningKeys.historyTitle')">
      <p>{{ t('helpCenter.adminSecuritySigningKeys.historyText') }}</p>
      <SigningKeyRecoveryHistory :recoveries="recoveries"/>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.adminSecuritySigningKeys.tip') }}</HelpTip>
  </HelpArticle>
</template>
