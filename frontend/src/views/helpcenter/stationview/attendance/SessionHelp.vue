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
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceSession.title')" :subtitle="t('helpCenter.attendanceSession.subtitle')">
    <!-- Dummy: Toolbar at the top (matching real view position) -->
    <HelpSection :title="t('helpCenter.attendanceSession.toolbarTitle')">
      <p>{{ t('helpCenter.attendanceSession.toolbarText') }}</p>
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']" disabled>
          {{ t('attendanceSession.back') }}
        </SecondaryButton>
        <div class="grid grid-cols-2 sm:flex sm:items-center gap-2">
          <SecondaryButton :icon="['fas', 'download']" disabled>
            {{ t('attendanceSession.export') }}
          </SecondaryButton>
          <SecondaryButton :icon="['fas', 'clipboard-check']" disabled>
            {{ t('attendanceSession.sync') }}
          </SecondaryButton>
          <PrimaryButton :icon="['fas', 'clipboard-user']" class="col-span-2" disabled>
            {{ t('attendanceSession.checkMode') }} (2)
          </PrimaryButton>
        </div>
      </div>
    </HelpSection>

    <!-- Session header editing -->
    <HelpSection :title="t('helpCenter.attendanceSession.headerTitle')">
      <p>{{ t('helpCenter.attendanceSession.headerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceSession.howTo')">
      <p>{{ t('helpCenter.attendanceSession.howToText') }}</p>
      <p>{{ t('helpCenter.attendanceSession.statusPresent') }}</p>
      <p>{{ t('helpCenter.attendanceSession.statusAbsent') }}</p>
      <p>{{ t('helpCenter.attendanceSession.statusDeclined') }}</p>
      <p>{{ t('helpCenter.attendanceSession.statusOpen') }}</p>
    </HelpSection>

    <!-- Dummy: Summary badges -->
    <HelpSection :title="t('helpCenter.attendanceSession.summaryTitle')">
      <div class="flex gap-3 text-sm flex-wrap">
        <SecondaryBadge>2 {{ t('attendanceSession.unconfirmed') }}</SecondaryBadge>
        <SuccessBadge>8 {{ t('attendanceSession.present') }}</SuccessBadge>
        <ErrorBadge>2 {{ t('attendanceSession.absent') }}</ErrorBadge>
        <InfoBadge>1 {{ t('attendanceSession.declined') }}</InfoBadge>
      </div>
    </HelpSection>

    <!-- Dummy: Attendance entries with check-in/check-out -->
    <HelpSection :title="t('helpCenter.attendanceSession.memberEntryTitle')">
      <p>{{ t('helpCenter.attendanceSession.memberEntryText') }}</p>
      <SubHeader>Anfaenger</SubHeader>
      <div class="space-y-1">
        <div class="rounded-lg px-4 py-3 border-l-4 transition-all border-success bg-success/5">
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
            <div class="flex items-center gap-2 min-w-0">
              <font-awesome-icon :icon="['fas', 'check']" class="h-4 w-4 text-success shrink-0"/>
              <span class="font-medium text-sm">Max Mustermann</span>
            </div>
            <div class="flex flex-col sm:flex-row sm:items-center gap-2">
              <div class="flex items-center gap-2">
                <SuccessButton class="opacity-40" disabled>
                  <font-awesome-icon :icon="['fas', 'check']"/>
                </SuccessButton>
                <ErrorButton disabled>
                  <font-awesome-icon :icon="['fas', 'xmark']"/>
                </ErrorButton>
                <InfoButton disabled>
                  <font-awesome-icon :icon="['fas', 'ban']"/>
                </InfoButton>
              </div>
              <div class="flex items-center gap-1 text-xs">
                <TimeShortInput :model-value="'18:00'" class="w-20 text-xs" disabled />
                <span class="text-(--text-muted)">–</span>
                <TimeShortInput :model-value="'20:15'" class="w-20 text-xs" disabled />
              </div>
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
              <SuccessButton disabled>
                <font-awesome-icon :icon="['fas', 'check']"/>
              </SuccessButton>
              <ErrorButton class="opacity-40" disabled>
                <font-awesome-icon :icon="['fas', 'xmark']"/>
              </ErrorButton>
              <InfoButton disabled>
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
              <SuccessButton disabled>
                <font-awesome-icon :icon="['fas', 'check']"/>
              </SuccessButton>
              <ErrorButton disabled>
                <font-awesome-icon :icon="['fas', 'xmark']"/>
              </ErrorButton>
              <InfoButton disabled>
                <font-awesome-icon :icon="['fas', 'ban']"/>
              </InfoButton>
            </div>
          </div>
        </div>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceSession.checkModeTitle')">
      <p>{{ t('helpCenter.attendanceSession.checkModeText') }}</p>
    </HelpSection>

    <!-- Dummy: Check mode -->
    <HelpSection :title="t('helpCenter.attendanceSession.checkModeExampleTitle')">
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('attendanceSession.checkMode') }}</SectionHeader>
        <div class="text-center space-y-4 py-4">
          <p class="text-2xl font-bold">Anna Schmidt</p>
          <p class="text-sm text-(--text-muted)">1 / 2</p>
          <div class="flex justify-center gap-4">
            <SuccessButton :icon="['fas', 'check']" disabled>
              {{ t('attendanceSession.present') }}
            </SuccessButton>
            <ErrorButton :icon="['fas', 'xmark']" disabled>
              {{ t('attendanceSession.absent') }}
            </ErrorButton>
            <InfoButton :icon="['fas', 'ban']" disabled>
              {{ t('attendanceSession.declined') }}
            </InfoButton>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Custom template fields -->
    <HelpSection :title="t('helpCenter.attendanceSession.fieldsTitle')">
      <p>{{ t('helpCenter.attendanceSession.fieldsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceSession.addMemberTitle')">
      <p>{{ t('helpCenter.attendanceSession.addMemberText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceSession.syncExportTitle')">
      <p>{{ t('helpCenter.attendanceSession.sync') }}</p>
      <p>{{ t('helpCenter.attendanceSession.export') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.attendanceSession.tip') }}</HelpTip>
  </HelpArticle>
</template>
