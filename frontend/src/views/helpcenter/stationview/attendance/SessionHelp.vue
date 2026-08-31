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
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MemberAttendanceRow from './sessionhelp/MemberAttendanceRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceSession.title')" :subtitle="t('helpCenter.attendanceSession.subtitle')">
    <!-- Dummy: Toolbar at the top (matching real view position), with its menu drawn open -->
    <HelpSection :title="t('helpCenter.attendanceSession.toolbarTitle')">
      <p>{{ t('helpCenter.attendanceSession.toolbarText') }}</p>
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']" disabled>
          {{ t('attendanceSession.back') }}
        </SecondaryButton>
        <div class="flex items-center gap-2">
          <PrimaryButton :icon="['fas', 'clipboard-user']" disabled>
            {{ t('attendanceSession.checkMode') }} (2)
          </PrimaryButton>
          <IconButton :icon="['fas', 'ellipsis-vertical']" :label="t('common.actions')" disabled/>
        </div>
      </div>
      <div class="mt-2 w-56 rounded-theme border border-(--border) py-1 ml-auto">
        <DropdownMenuItem :icon="['fas', 'download']">{{ t('attendanceSession.export') }}</DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'clipboard-check']">{{ t('attendanceSession.sync') }}</DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'trash']" destructive>{{ t('attendanceSession.delete') }}</DropdownMenuItem>
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
      <SubHeader>Anfänger</SubHeader>
      <div class="space-y-1">
        <MemberAttendanceRow name="Max Mustermann" status="present" check-in="18:00" check-out="20:15"/>
        <MemberAttendanceRow name="Erika Muster" status="absent"/>
        <MemberAttendanceRow name="Anna Schmidt" status="open"/>
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
