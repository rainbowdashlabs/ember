/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div>
        <MutedText tag="p" size="sm">{{ t('adminSecurity.hub.hint') }}</MutedText>
      </div>

      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('adminSecurity.hub.configTitle') }}</SubHeader>
        <ul class="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
          <li>
            <RouterLink :to="{ name: 'admin-security-tokens' }" class="underline">
              {{ t('adminSecurity.tokens.title') }}
            </RouterLink>
          </li>
          <li>
            <RouterLink :to="{ name: 'admin-security-hibp' }" class="underline">
              {{ t('adminSecurity.hibp.title') }}
            </RouterLink>
          </li>
          <li>
            <RouterLink :to="{ name: 'admin-security-two-factor' }" class="underline">
              {{ t('adminSecurity.twoFactor.title') }}
            </RouterLink>
          </li>
        </ul>
      </NeutralContainer>

      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('adminSecurity.hub.operationsTitle') }}</SubHeader>
        <ul class="text-sm">
          <li>
            <RouterLink :to="{ name: 'admin-two-factor' }" class="underline">
              {{ t('sidebar.twoFactor') }}
            </RouterLink>
          </li>
        </ul>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
