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
  <HelpArticle :title="t('helpCenter.courses.title')" :subtitle="t('helpCenter.courses.subtitle')">
    <HelpSection :title="t('helpCenter.courses.whatIs')">
      <p>{{ t('helpCenter.courses.whatIsText') }}</p>
      <p>{{ t('helpCenter.courses.whatIsExample') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courses.howTo')">
      <p>{{ t('helpCenter.courses.howToText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courses.exampleTitle')">
      <div class="space-y-2">
        <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
          <div class="flex justify-end">
            <PrimaryButton :icon="['fas', 'plus']">{{ t('courses.create') }}</PrimaryButton>
          </div>
        </HelpPermissionGuard>
        <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <p class="font-medium">{{ t('helpCenter.courses.exampleName') }}</p>
            <MutedText tag="p" size="sm">{{ t('courses.partCount', {count: 4}) }}</MutedText>
          </div>
          <MutedText tag="span" size="sm">{{ t('courses.places', {holding: 7, limit: 12}) }}</MutedText>
        </NeutralContainer>
        <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <p class="font-medium">{{ t('helpCenter.courses.exampleOther') }}</p>
            <MutedText tag="p" size="sm">{{ t('courses.partCount', {count: 2}) }}</MutedText>
          </div>
          <ErrorBadge>{{ t('courses.cancelled') }}</ErrorBadge>
        </NeutralContainer>
      </div>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.courses.tip') }}</HelpTip>
  </HelpArticle>
</template>
