/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('adminSettings.title') }}</SectionHeader>

    <div class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ t('adminSettings.stationRegistration') }}</div>
        <div class="text-sm text-(--text-muted)">{{ t('adminSettings.stationRegistrationHint') }}</div>
      </div>
      <ToggleInput :model-value="true" />
    </div>

    <div class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ t('adminSettings.forcePrideFlag') }}</div>
        <div class="text-sm text-(--text-muted)">{{ t('adminSettings.forcePrideFlagHint') }}</div>
      </div>
      <ToggleInput :model-value="false" />
    </div>
  </NeutralContainer>
</template>
