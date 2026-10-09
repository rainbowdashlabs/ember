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
import NewSignatureSection from '@/views/accountview/accountsignatureview/NewSignatureSection.vue'
import SignatureConsentSection from '@/views/accountview/accountsignatureview/SignatureConsentSection.vue'
import {SignatureImageSource} from '@/api/generated/schema'
import type {SignatureSettingsResponse} from '@/api/generated/schema'

const {t} = useI18n()

const EXAMPLE: SignatureSettingsResponse = {
  hasImage: true,
  imageSource: SignatureImageSource.DRAWN,
  imageSavedAt: '2026-10-08T09:25:00Z',
  autoSignConsentedAt: '2026-10-08T09:30:00Z',
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.signature.title')" :subtitle="t('helpCenter.basics.signature.subtitle')">
    <HelpSection :title="t('helpCenter.basics.signature.whatIs')">
      <p>{{ t('helpCenter.basics.signature.whatIsText') }}</p>
      <p>{{ t('helpCenter.basics.signature.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.signature.howTo')">
      <p>{{ t('helpCenter.basics.signature.step1') }}</p>
      <p>{{ t('helpCenter.basics.signature.step2') }}</p>
      <p>{{ t('helpCenter.basics.signature.step3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.signature.exampleTitle')">
      <NewSignatureSection :busy="false"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.signature.consentTitle')">
      <p>{{ t('helpCenter.basics.signature.consentText') }}</p>
      <p>{{ t('helpCenter.basics.signature.consentText2') }}</p>
      <SignatureConsentSection :settings="EXAMPLE" :busy="false"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.signature.withoutTitle')">
      <p>{{ t('helpCenter.basics.signature.withoutText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.signature.tip') }}</HelpTip>
  </HelpArticle>
</template>
