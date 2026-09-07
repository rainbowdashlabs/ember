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

const {t} = useI18n()
</script>

<template>
  <HelpArticle
      :title="t('helpCenter.adminSecurityTwoFactor.title')"
      :subtitle="t('helpCenter.adminSecurityTwoFactor.subtitle')">
    <HelpSection :title="t('helpCenter.adminSecurityTwoFactor.whatIs')">
      <p>{{ t('helpCenter.adminSecurityTwoFactor.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityTwoFactor.fields')">
      <p>{{ t('helpCenter.adminSecurityTwoFactor.fieldsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityTwoFactor.secretKey')">
      <p>{{ t('helpCenter.adminSecurityTwoFactor.secretKeyText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityTwoFactor.totp')">
      <p>{{ t('helpCenter.adminSecurityTwoFactor.totpText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityTwoFactor.backupCodes')">
      <p>{{ t('helpCenter.adminSecurityTwoFactor.backupCodesText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityTwoFactor.webauthn')">
      <p>{{ t('helpCenter.adminSecurityTwoFactor.webauthnText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityTwoFactor.passkeys')">
      <p>{{ t('helpCenter.adminSecurityTwoFactor.passkeysText') }}</p>
      <p>{{ t('helpCenter.adminSecurityTwoFactor.passkeysModesText') }}</p>
      <p>{{ t('helpCenter.adminSecurityTwoFactor.passkeysReadinessText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.adminSecurityTwoFactor.tip') }}</HelpTip>
  </HelpArticle>
</template>
