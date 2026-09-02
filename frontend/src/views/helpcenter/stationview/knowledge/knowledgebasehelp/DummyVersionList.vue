/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-2">
      <div class="flex items-center justify-between text-sm">
        <span>{{ t('kb.version') }} 3 <SecondaryBadge>{{ t('helpCenter.kb.versionCurrent') }}</SecondaryBadge></span>
        <span class="text-xs text-(--text-muted)">{{ t('helpCenter.kb.versionAgeRecent') }}</span>
      </div>
      <div class="flex items-center justify-between text-sm">
        <span>{{ t('kb.version') }} 2</span>
        <span class="text-xs text-(--text-muted)">{{ t('helpCenter.kb.versionAgeDay') }}</span>
      </div>
      <div class="flex items-center justify-between text-sm">
        <span>{{ t('kb.version') }} 1</span>
        <span class="text-xs text-(--text-muted)">{{ t('helpCenter.kb.versionAgeDays') }}</span>
      </div>
    </div>
  </NeutralContainer>
</template>
