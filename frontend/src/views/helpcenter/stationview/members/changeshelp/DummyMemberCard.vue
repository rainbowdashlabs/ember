/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import DummyChangeRow from './DummyChangeRow.vue'
import DummyAckedRow from './DummyAckedRow.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-between flex-wrap gap-2 cursor-pointer">
      <div class="flex items-center gap-2">
        <MutedIcon :icon="['fas', 'chevron-down']"/>
        <div>
          <span class="font-semibold text-sm">Max Mustermann</span>
          <p class="text-xs text-(--text-muted)">
            {{ t('memberChanges.lastChange') }}: 14.05.2026, 15:30
          </p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <ErrorBadge>2 {{ t('memberChanges.pending') }}</ErrorBadge>
        <SuccessButton :icon="['fas', 'check-double']">{{ t('memberDetail.acknowledgeAll') }}</SuccessButton>
        <SecondaryButton :icon="['fas', 'user']">{{ t('memberChanges.toProfile') }}</SecondaryButton>
      </div>
    </div>
    <div class="mt-4 space-y-3">
      <DummyChangeRow/>
      <DummyAckedRow/>
    </div>
  </NeutralContainer>
</template>
