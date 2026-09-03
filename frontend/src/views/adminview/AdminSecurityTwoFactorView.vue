/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import TwoFactorCorePanel from '@/views/adminview/adminsecuritytwofactorview/TwoFactorCorePanel.vue'
import PoliciesPanel from '@/views/adminview/adminsecuritytwofactorview/PoliciesPanel.vue'
import TotpPanel from '@/views/adminview/adminsecuritytwofactorview/TotpPanel.vue'
import BackupCodesPanel from '@/views/adminview/adminsecuritytwofactorview/BackupCodesPanel.vue'
import WebAuthnPanel from '@/views/adminview/adminsecuritytwofactorview/WebAuthnPanel.vue'
import PasskeysPanel from '@/views/adminview/adminsecuritytwofactorview/PasskeysPanel.vue'

const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.admin-security-two-factor.title')" :subtitle="t('pages.admin-security-two-factor.subtitle')">
    <div class="space-y-6">
      <TwoFactorCorePanel/>
      <PoliciesPanel/>
      <TotpPanel/>
      <BackupCodesPanel/>
      <WebAuthnPanel/>
      <PasskeysPanel/>
    </div>
  </ViewContent>
</template>
