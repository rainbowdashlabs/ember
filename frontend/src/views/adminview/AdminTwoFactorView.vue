/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AuditPanel from '@/views/adminview/admintwofactorview/AuditPanel.vue'

/**
 * The security history of every account. Resetting an account's second factor moved to the
 * account list, where the account is opened with everything else that can be done to it.
 */
const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.admin-two-factor.title')" :subtitle="t('pages.admin-two-factor.subtitle')">
    <div class="space-y-6">
      <MutedText tag="p" size="sm">
        {{ t('twoFactor.admin.resetMoved') }}
        <RouterLink :to="{ name: 'admin-accounts' }" class="underline">{{ t('sidebar.accounts') }}</RouterLink>
      </MutedText>
      <AuditPanel/>
    </div>
  </ViewContent>
</template>
