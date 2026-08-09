/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {MemberIdentity} from '@/api/types'

defineProps<{
  members: MemberIdentity[]
}>()

const {t} = useI18n()
</script>

<template>
  <InfoContainer>
    <div class="space-y-3">
      <div class="flex items-center gap-2 flex-wrap">
        <SectionHeader>{{ t('forms.analytics.missingTitle') }}</SectionHeader>
        <InfoBadge>{{ t('forms.analytics.missingCount', {count: members.length}) }}</InfoBadge>
      </div>
      <p class="text-sm text-(--text-muted)">{{ t('forms.analytics.missingHelp') }}</p>
      <ul class="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <li v-for="member in members" :key="member.memberUid ?? member.name ?? ''" class="min-w-0">
          <MemberName :identity="member"/>
        </li>
      </ul>
    </div>
  </InfoContainer>
</template>
