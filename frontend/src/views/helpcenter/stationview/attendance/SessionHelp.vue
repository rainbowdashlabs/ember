/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceSession.title')" :subtitle="t('helpCenter.attendanceSession.subtitle')">
    <HelpSection :title="t('helpCenter.attendanceSession.howTo')">
      <p>{{ t('helpCenter.attendanceSession.howToText') }}</p>
      <p>{{ t('helpCenter.attendanceSession.statusPresent') }}</p>
      <p>{{ t('helpCenter.attendanceSession.statusAbsent') }}</p>
      <p>{{ t('helpCenter.attendanceSession.statusDeclined') }}</p>
      <p>{{ t('helpCenter.attendanceSession.statusOpen') }}</p>
    </HelpSection>

    <!-- Dummy: Summary badges -->
    <div class="flex gap-3 text-sm flex-wrap">
      <SecondaryBadge>2 {{ t('attendanceSession.unconfirmed') }}</SecondaryBadge>
      <SuccessBadge>8 {{ t('attendanceSession.present') }}</SuccessBadge>
      <ErrorBadge>2 {{ t('attendanceSession.absent') }}</ErrorBadge>
      <InfoBadge>1 {{ t('attendanceSession.declined') }}</InfoBadge>
    </div>

    <!-- Dummy: Attendance table -->
    <SubHeader>Anfänger</SubHeader>
    <div class="space-y-1">
      <div class="rounded-lg px-4 py-3 border-l-4 transition-all border-success bg-success/5">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div class="flex items-center gap-2 min-w-0">
            <font-awesome-icon :icon="['fas', 'check']" class="h-4 w-4 text-success shrink-0"/>
            <span class="font-medium text-sm">Max Mustermann</span>
          </div>
          <div class="flex items-center gap-2">
            <SuccessButton class="opacity-40" disabled>
              <font-awesome-icon :icon="['fas', 'check']"/>
            </SuccessButton>
            <ErrorButton>
              <font-awesome-icon :icon="['fas', 'xmark']"/>
            </ErrorButton>
            <InfoButton>
              <font-awesome-icon :icon="['fas', 'ban']"/>
            </InfoButton>
          </div>
        </div>
      </div>
      <div class="rounded-lg px-4 py-3 border-l-4 transition-all border-error bg-error/5">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div class="flex items-center gap-2 min-w-0">
            <font-awesome-icon :icon="['fas', 'xmark']" class="h-4 w-4 text-error shrink-0"/>
            <span class="font-medium text-sm">Erika Muster</span>
          </div>
          <div class="flex items-center gap-2">
            <SuccessButton>
              <font-awesome-icon :icon="['fas', 'check']"/>
            </SuccessButton>
            <ErrorButton class="opacity-40" disabled>
              <font-awesome-icon :icon="['fas', 'xmark']"/>
            </ErrorButton>
            <InfoButton>
              <font-awesome-icon :icon="['fas', 'ban']"/>
            </InfoButton>
          </div>
        </div>
      </div>
      <div class="rounded-lg px-4 py-3 border-l-4 transition-all border-bg-light-accent dark:border-bg-dark-accent bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div class="flex items-center gap-2 min-w-0">
            <font-awesome-icon :icon="['fas', 'asterisk']" class="h-4 w-4 text-(--text-muted) shrink-0"/>
            <span class="font-medium text-sm">Anna Schmidt</span>
          </div>
          <div class="flex items-center gap-2">
            <SuccessButton>
              <font-awesome-icon :icon="['fas', 'check']"/>
            </SuccessButton>
            <ErrorButton>
              <font-awesome-icon :icon="['fas', 'xmark']"/>
            </ErrorButton>
            <InfoButton>
              <font-awesome-icon :icon="['fas', 'ban']"/>
            </InfoButton>
          </div>
        </div>
      </div>
    </div>

    <HelpSection :title="t('helpCenter.attendanceSession.checkModeTitle')">
      <p>{{ t('helpCenter.attendanceSession.checkModeText') }}</p>
    </HelpSection>

    <!-- Dummy: Check mode -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('attendanceSession.checkMode') }}</SectionHeader>
      <div class="text-center space-y-4 py-4">
        <p class="text-2xl font-bold">Anna Schmidt</p>
        <p class="text-sm text-(--text-muted)">1 / 2</p>
        <div class="flex justify-center gap-4">
          <SuccessButton :icon="['fas', 'check']">
            {{ t('attendanceSession.present') }}
          </SuccessButton>
          <ErrorButton :icon="['fas', 'xmark']">
            {{ t('attendanceSession.absent') }}
          </ErrorButton>
          <InfoButton :icon="['fas', 'ban']">
            {{ t('attendanceSession.declined') }}
          </InfoButton>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceSession.addMemberTitle')">
      <p>{{ t('helpCenter.attendanceSession.addMemberText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceSession.syncExportTitle')">
      <p>{{ t('helpCenter.attendanceSession.sync') }}</p>
      <p>{{ t('helpCenter.attendanceSession.export') }}</p>
    </HelpSection>

    <!-- Dummy: Action buttons -->
    <div class="flex items-center gap-2">
      <SecondaryButton :icon="['fas', 'download']">
        {{ t('attendanceSession.export') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'clipboard-check']">
        {{ t('attendanceSession.sync') }}
      </SecondaryButton>
      <PrimaryButton :icon="['fas', 'clipboard-user']">
        {{ t('attendanceSession.checkMode') }} (2)
      </PrimaryButton>
    </div>
  </HelpArticle>
</template>
