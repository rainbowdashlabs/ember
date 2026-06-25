/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import MutedText from '@/components/typography/MutedText.vue'

defineProps<{
  accountUid?: string
  fullName: string
  accountEmail: string
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('profile.accountTitle') }}</SectionHeader>
    <div class="flex items-center gap-4">
      <UserAvatar
          :identity="accountUid ? { accountUid } : undefined"
          :name="fullName"
          size="lg"
      />
      <div class="space-y-1">
        <div class="font-medium">{{ fullName }}</div>
        <div class="text-sm text-(--text-muted)">{{ accountEmail }}</div>
      </div>
    </div>
    <MutedText size="sm">
      {{ t('profile.manageInAccount') }}
      <router-link :to="{ name: 'account' }" class="underline">{{ t('profile.manageInAccountLink') }}</router-link>
    </MutedText>
  </NeutralContainer>
</template>
