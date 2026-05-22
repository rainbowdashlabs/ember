/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpRole} from '@/components/helpcenter/HelpRoleToggle.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

const {t} = useI18n()

const roles: HelpRole[] = [
  {key: 'member', label: t('helpCenter.roles.member')},
  {key: 'team', label: t('helpCenter.roles.team')},
  {key: 'manager', label: t('helpCenter.roles.manager')},
]
const activeRole = ref('')
</script>

<template>
  <HelpArticle :title="t('helpCenter.dashboard.title')" :subtitle="t('helpCenter.dashboard.subtitle')">
    <HelpSection :title="t('helpCenter.dashboard.whatIs')">
      <p>{{ t('helpCenter.dashboard.whatIsText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeRole" :roles="roles"/>

    <HelpSection :title="t('helpCenter.dashboard.whatYouSee')">
      <p>{{ t('helpCenter.dashboard.notifications') }}</p>
      <p>{{ t('helpCenter.dashboard.exchanges') }}</p>
      <p>{{ t('helpCenter.dashboard.registrations') }}</p>
      <p>{{ t('helpCenter.dashboard.profileIncomplete') }}</p>
    </HelpSection>

    <!-- Dummy: Profile incomplete banner -->
    <ErrorContainer class="flex items-center justify-between gap-4">
      <div>
        <p class="font-semibold text-sm">{{ t('dashboard.profileIncomplete') }}</p>
        <p class="text-xs">{{ t('dashboard.profileIncompleteHint') }}</p>
      </div>
      <PrimaryButton class="shrink-0">{{ t('dashboard.completeProfile') }}</PrimaryButton>
    </ErrorContainer>

    <HelpSection :title="t('helpCenter.dashboard.notificationsTitle')">
      <p>{{ t('helpCenter.dashboard.notificationsBadge') }}</p>
      <p>{{ t('helpCenter.dashboard.notificationsAction') }}</p>
    </HelpSection>

    <!-- Dummy: Notifications panel -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between">
        <SectionHeader>
          <font-awesome-icon :icon="['fas', 'bell']" class="mr-2"/>
          {{ t('dashboard.notifications') }} (2)
        </SectionHeader>
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'check-double']" class="mr-1"/>
          {{ t('dashboard.acknowledgeAll') }}
        </SecondaryButton>
      </div>
      <NeutralContainer class="flex items-start justify-between gap-3 py-2 px-3">
        <div class="flex items-start gap-3">
          <font-awesome-icon :icon="['fas', 'newspaper']" class="text-primary mt-0.5 h-4 w-4 shrink-0"/>
          <div>
            <span class="text-xs font-semibold text-(--text-muted)">{{ t('notification.typeLabel.NEW_NEWS') }}</span>
            <p class="text-sm">{{ t('notification.newNews', {title: 'Dienstplanänderung Mai'}) }}</p>
            <p class="text-xs text-(--text-muted)">14.05.2026, 10:30</p>
          </div>
        </div>
        <button type="button" class="text-xs text-primary hover:underline shrink-0 mt-1">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-0.5"/>
          {{ t('dashboard.acknowledge') }}
        </button>
      </NeutralContainer>
      <NeutralContainer class="flex items-start justify-between gap-3 py-2 px-3">
        <div class="flex items-start gap-3">
          <font-awesome-icon :icon="['fas', 'calendar-plus']" class="text-primary mt-0.5 h-4 w-4 shrink-0"/>
          <div>
            <span class="text-xs font-semibold text-(--text-muted)">{{ t('notification.typeLabel.NEW_EVENT') }}</span>
            <p class="text-sm">{{ t('notification.newEvent', {title: 'Übungsabend'}) }}</p>
            <p class="text-xs text-(--text-muted)">13.05.2026, 18:00</p>
          </div>
        </div>
        <button type="button" class="text-xs text-primary hover:underline shrink-0 mt-1">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-0.5"/>
          {{ t('dashboard.acknowledge') }}
        </button>
      </NeutralContainer>
    </NeutralContainer>

    <!-- Dummy: Exchange requests panel -->
    <NeutralContainer class="space-y-3">
      <SectionHeader>
        <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2"/>
        {{ t('dashboard.exchanges') }}
      </SectionHeader>
      <NeutralContainer class="flex items-center justify-between gap-3 py-2 px-3">
        <div>
          <p class="text-sm font-medium">Helme</p>
          <p class="text-xs text-(--text-muted)">M → L</p>
          <p class="text-xs text-(--text-muted)">Helm passt nicht mehr</p>
        </div>
        <InfoBadge>{{ t('dashboard.exchangeStatus.ANNOUNCED') }}</InfoBadge>
      </NeutralContainer>
    </NeutralContainer>

    <!-- Dummy: Registrations panel -->
    <NeutralContainer class="space-y-3">
      <SectionHeader>
        <font-awesome-icon :icon="['fas', 'calendar-days']" class="mr-2"/>
        {{ t('dashboard.registrations') }}
      </SectionHeader>
      <NeutralContainer class="flex items-center justify-between gap-3 py-2 px-3">
        <div>
          <p class="text-sm font-medium">Übungsabend</p>
          <p class="text-xs text-(--text-muted)">20.05.2026</p>
        </div>
        <SuccessBadge>{{ t('dashboard.registrationStatus.ACCEPTED') }}</SuccessBadge>
      </NeutralContainer>
      <NeutralContainer class="flex items-center justify-between gap-3 py-2 px-3">
        <div>
          <p class="text-sm font-medium">Wettkampf Vorbereitung</p>
          <p class="text-xs text-(--text-muted)">25.05.2026</p>
        </div>
        <ErrorBadge>{{ t('dashboard.registrationStatus.PENDING') }}</ErrorBadge>
      </NeutralContainer>
    </NeutralContainer>

    <template v-if="activeRole === 'team' || activeRole === 'manager'">
      <HelpSection :title="t('helpCenter.dashboard.teamManagerExtra')">
        <p>{{ t('helpCenter.dashboard.teamManagerExtraText') }}</p>
        <p>{{ t('helpCenter.dashboard.managedRegistrations') }}</p>
      </HelpSection>
    </template>

    <HelpTip>{{ t('helpCenter.dashboard.tip') }}</HelpTip>
  </HelpArticle>
</template>
