/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

const {t} = useI18n()
</script>

<template>
  <div class="rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 border-l-4 border-primary space-y-2">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-semibold text-sm">Telefon</span>
        <span class="text-xs text-(--text-muted)">14.05.2026, 15:30</span>
        <span class="text-xs text-(--text-muted)">{{ t('memberDetail.changedBy') }}: Max Mustermann</span>
      </div>
      <ErrorBadge>{{ t('memberDetail.notAcknowledged') }}</ErrorBadge>
    </div>
    <div class="flex items-center gap-2 text-xs">
      <span class="text-(--text-muted)">0170 1111111</span>
      <MutedIcon :icon="['fas', 'chevron-right']"/>
      <span class="font-medium">0170 2222222</span>
    </div>
    <div class="flex items-center gap-2 pt-1">
      <PrimaryButton :icon="['fas', 'check']">{{ t('memberDetail.acknowledge') }}</PrimaryButton>
      <SecondaryButton :icon="['fas', 'comment']">{{ t('memberDetail.acknowledgeWithComment') }}</SecondaryButton>
    </div>
    <div class="space-y-2 pt-1">
      <TextAreaInput :model-value="''" :placeholder="t('memberDetail.commentPlaceholder')" class="text-sm"/>
      <PrimaryButton>{{ t('memberDetail.submitAcknowledge') }}</PrimaryButton>
    </div>
  </div>
</template>
