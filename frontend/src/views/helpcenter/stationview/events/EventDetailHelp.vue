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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventDetail.title')" :subtitle="t('helpCenter.eventDetail.subtitle')">
    <HelpSection :title="t('helpCenter.eventDetail.whatIs')">
      <p>{{ t('helpCenter.eventDetail.whatIsText') }}</p>
      <p>{{ t('helpCenter.eventDetail.memberAccess') }}</p>
    </HelpSection>

    <!-- Dummy: Event detail view -->
    <div class="space-y-4">
      <!-- Event header -->
      <div class="flex items-center gap-2 flex-wrap">
        <SectionHeader class="m-0">Wettkampf Vorbereitung</SectionHeader>
        <SecondaryBadge>{{ t('events.typeOneTime') }}</SecondaryBadge>
      </div>

      <!-- Date/time/category info -->
      <NeutralContainer class="space-y-2 text-sm">
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'calendar']" class="text-(--text-muted) w-4"/>
          <span>Samstag, 25.05.2026</span>
        </div>
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'clock']" class="text-(--text-muted) w-4"/>
          <span>14:00 – 17:00</span>
        </div>
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'tag']" class="text-(--text-muted) w-4"/>
          <span>Wettkampf</span>
        </div>
        <p class="text-(--text-muted) pt-1">Vorbereitung auf den Kreiswettkampf. Bitte pünktlich erscheinen.</p>
      </NeutralContainer>

      <!-- Registration section -->
      <SubHeader>{{ t('helpCenter.eventDetail.registrationTitle') }}</SubHeader>
      <NeutralContainer class="space-y-3">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="text-sm font-medium">{{ t('helpCenter.eventDetail.yourStatus') }}:</span>
          <InfoBadge>{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
        </div>
        <div class="flex gap-2 flex-wrap">
          <PrimaryButton disabled>
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
            {{ t('eventsUpcoming.register') }}
          </PrimaryButton>
          <ErrorButton disabled>
            <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
            {{ t('eventsUpcoming.decline') }}
          </ErrorButton>
        </div>
      </NeutralContainer>
    </div>

    <!-- Dummy: Registrations list -->
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

    <HelpSection :title="t('helpCenter.eventDetail.badgesTitle')">
      <p>{{ t('helpCenter.eventDetail.badgesText') }}</p>
      <div class="flex flex-wrap gap-2 mt-3">
        <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
        <InfoBadge>{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
        <ErrorBadge>{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
        <ErrorBadge>{{ t('eventsUpcoming.statusDeclined') }}</ErrorBadge>
      </div>
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
