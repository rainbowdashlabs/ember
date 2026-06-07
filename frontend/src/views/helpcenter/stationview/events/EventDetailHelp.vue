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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventDetail.title')" :subtitle="t('helpCenter.eventDetail.subtitle')">
    <HelpSection :title="t('helpCenter.eventDetail.whatIs')">
      <p>{{ t('helpCenter.eventDetail.whatIsText') }}</p>
      <p>{{ t('helpCenter.eventDetail.memberAccess') }}</p>
    </HelpSection>

    <!-- Dummy: Event detail view -->
    <HelpSection :title="t('events.general')">
      <!-- Event header -->
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div class="flex items-center gap-3">
          <SectionHeader>Wettkampf Vorbereitung</SectionHeader>
          <SecondaryBadge>{{ t('events.typeOneTime') }}</SecondaryBadge>
        </div>
        <div class="flex items-center gap-2">
          <SecondaryButton><font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>{{ t('common.back') }}</SecondaryButton>
          <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
            <ErrorButton><font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>{{ t('events.cancelEvent') }}</ErrorButton>
            <PrimaryButton><font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/>{{ t('events.editEvent') }}</PrimaryButton>
          </HelpPermissionGuard>
        </div>
      </div>

      <!-- Date/time/category info -->
      <NeutralContainer class="space-y-3 mt-3">
        <SubHeader>{{ t('events.general') }}</SubHeader>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="sm:col-span-2">
            <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.description') }}</span>
            <p class="text-sm mt-1">Vorbereitung auf den Kreiswettkampf. Bitte pünktlich erscheinen.</p>
          </div>
          <div>
            <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.category') }}</span>
            <p class="text-sm">Wettkampf</p>
          </div>
          <div>
            <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.date') }}</span>
            <p class="text-sm">25.05.2026</p>
          </div>
          <div>
            <span class="text-xs font-medium text-(--text-muted) uppercase">{{ t('events.startTime') }} – {{ t('events.endTime') }}</span>
            <p class="text-sm">14:00 – 17:00</p>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Registration section -->
    <HelpSection :title="t('helpCenter.eventDetail.registrationTitle')">
      <NeutralContainer class="space-y-3">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="text-sm font-medium">{{ t('helpCenter.eventDetail.yourStatus') }}:</span>
          <InfoBadge>{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
        </div>
        <div class="flex gap-2 flex-wrap">
          <PrimaryButton :icon="['fas', 'check']" disabled>
            {{ t('eventsUpcoming.register') }}
          </PrimaryButton>
          <ErrorButton :icon="['fas', 'xmark']" disabled>
            {{ t('eventsUpcoming.decline') }}
          </ErrorButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Registrations list -->
    <HelpPermissionGuard :permissions="[StationPermission.EVENT_REGISTRATION]" :label="t('helpCenter.permissionLabel.eventManage')">
      <HelpSection :title="t('eventDetail.registrations')">
        <NeutralContainer class="space-y-2">
          <SubHeader>{{ t('eventDetail.registrations') }}</SubHeader>
          <div class="flex flex-wrap gap-2">
            <SuccessBadge>3 {{ t('eventsUpcoming.accepted') }}</SuccessBadge>
            <InfoBadge>1 {{ t('eventsUpcoming.pendingCount') }}</InfoBadge>
          </div>
          <div class="space-y-1 text-sm">
            <div class="flex items-center gap-2">
              <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
              <span>Max Mustermann</span>
            </div>
            <div class="flex items-center gap-2">
              <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
              <span>Lisa Schmidt</span>
            </div>
            <div class="flex items-center gap-2">
              <InfoBadge>{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
              <span>Tom Müller</span>
            </div>
          </div>
        </NeutralContainer>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.eventDetail.badgesTitle')">
      <p>{{ t('helpCenter.eventDetail.badgesText') }}</p>
      <div class="flex flex-wrap gap-2 mt-3">
        <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
        <InfoBadge>{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
        <ErrorBadge>{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
        <ErrorBadge>{{ t('eventsUpcoming.statusDeclined') }}</ErrorBadge>
      </div>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
      <HelpSection :title="t('helpCenter.eventDetail.cancelTitle')">
        <p>{{ t('helpCenter.eventDetail.cancelText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.eventDetail.nextOccurrenceTitle')">
      <p>{{ t('helpCenter.eventDetail.nextOccurrenceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.commentsTitle')">
      <p>{{ t('helpCenter.eventDetail.commentsText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
      <HelpSection :title="t('helpCenter.eventDetail.notesTitle')">
        <p>{{ t('helpCenter.eventDetail.notesText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.eventDetail.federationTitle')">
      <p>{{ t('helpCenter.eventDetail.federationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.absentTitle')">
      <p>{{ t('helpCenter.eventDetail.absentText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.templateTitle')">
      <p>{{ t('helpCenter.eventDetail.templateText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
