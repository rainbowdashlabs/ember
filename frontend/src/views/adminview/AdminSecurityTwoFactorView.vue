/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ViewContent from '@/components/layout/ViewContent.vue'
import TwoFactorCorePanel from '@/views/adminview/adminsecuritytwofactorview/TwoFactorCorePanel.vue'
import PoliciesPanel from '@/views/adminview/adminsecuritytwofactorview/PoliciesPanel.vue'
import TotpPanel from '@/views/adminview/adminsecuritytwofactorview/TotpPanel.vue'
import BackupCodesPanel from '@/views/adminview/adminsecuritytwofactorview/BackupCodesPanel.vue'
import WebAuthnPanel from '@/views/adminview/adminsecuritytwofactorview/WebAuthnPanel.vue'
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <TwoFactorCorePanel/>
      <PoliciesPanel/>
      <TotpPanel/>
      <BackupCodesPanel/>
      <WebAuthnPanel/>
    </div>
  </ViewContent>
</template>
