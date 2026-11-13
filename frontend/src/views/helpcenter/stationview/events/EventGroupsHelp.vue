/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {StationPermission} from '@/api/generated/schema'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventGroups.title')" :subtitle="t('helpCenter.eventGroups.subtitle')">
    <HelpSection :title="t('helpCenter.eventGroups.whatIs')">
      <p>{{ t('helpCenter.eventGroups.whatIsText') }}</p>
      <p>{{ t('helpCenter.eventGroups.whatIsExample') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroups.howTo')">
      <p>{{ t('helpCenter.eventGroups.howToText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroups.exampleTitle')">
      <div class="space-y-2">
        <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
          <div class="flex justify-end">
            <PrimaryButton :icon="['fas', 'plus']">{{ t('eventGroups.create') }}</PrimaryButton>
          </div>
        </HelpPermissionGuard>
        <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <p class="font-medium">{{ t('helpCenter.eventGroups.exampleName') }}</p>
            <MutedText tag="p" size="sm">{{ t('eventGroups.partCount', {count: 4}) }}</MutedText>
          </div>
          <MutedText tag="span" size="sm">{{ t('eventGroups.places', {holding: 7, limit: 12}) }}</MutedText>
        </NeutralContainer>
        <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <p class="font-medium">{{ t('helpCenter.eventGroups.exampleOther') }}</p>
            <MutedText tag="p" size="sm">{{ t('eventGroups.partCount', {count: 2}) }}</MutedText>
          </div>
          <ErrorBadge>{{ t('eventGroups.cancelled') }}</ErrorBadge>
        </NeutralContainer>
      </div>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.eventGroups.tip') }}</HelpTip>
  </HelpArticle>
</template>
