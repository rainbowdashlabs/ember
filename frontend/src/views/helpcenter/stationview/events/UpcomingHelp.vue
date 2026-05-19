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
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()

const roles: HelpRole[] = [
  {key: 'member', label: t('helpCenter.roles.member')},
  {key: 'guardian', label: t('helpCenter.roles.memberManager')},
]
const activeRole = ref('')
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventsUpcoming.title')" :subtitle="t('helpCenter.eventsUpcoming.subtitle')">
    <HelpSection :title="t('helpCenter.eventsUpcoming.whatShown')">
      <p>{{ t('helpCenter.eventsUpcoming.whatShownText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeRole" :roles="roles"/>

    <!-- Dummy: Today's events -->
    <SectionHeader>{{ t('eventsUpcoming.today') }}</SectionHeader>
    <div class="grid gap-3 sm:grid-cols-2">
      <PrimaryContainer class="space-y-2">
        <div class="flex items-center justify-between">
          <span class="font-semibold">Übungsabend</span>
          <span class="text-sm">18:00 – 20:00</span>
        </div>
        <p class="text-sm text-(--text-muted)">Regulärer Übungsabend</p>
      </PrimaryContainer>
    </div>

    <HelpSection :title="t('helpCenter.eventsUpcoming.registrationTitle')">
      <p>{{ t('helpCenter.eventsUpcoming.registrationText') }}</p>
      <p>{{ t('helpCenter.eventsUpcoming.statusPending') }}</p>
      <p>{{ t('helpCenter.eventsUpcoming.statusAccepted') }}</p>
      <p>{{ t('helpCenter.eventsUpcoming.statusDenied') }}</p>
      <p>{{ t('helpCenter.eventsUpcoming.statusDeclined') }}</p>
    </HelpSection>

    <!-- Dummy: Upcoming event with registration -->
    <SectionHeader>{{ t('eventsUpcoming.upcoming') }}</SectionHeader>
    <div class="space-y-2">
      <NeutralContainer class="space-y-2">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span class="font-medium">Wettkampf Vorbereitung</span>
            <span class="ml-2 text-sm text-(--text-muted)">Samstag, 2026-05-25</span>
            <span class="ml-2 text-xs text-(--text-muted)">14:00 – 17:00</span>
          </div>
          <div class="flex items-center gap-2 text-xs">
            <SuccessBadge>5 {{ t('eventsUpcoming.accepted') }}</SuccessBadge>
            <InfoBadge>2 {{ t('eventsUpcoming.pendingCount') }}</InfoBadge>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <PrimaryButton class="text-sm">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
            {{ t('eventsUpcoming.register') }}
          </PrimaryButton>
          <ErrorButton class="text-sm">
            <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
            {{ t('eventsUpcoming.decline') }}
          </ErrorButton>
        </div>
      </NeutralContainer>
      <NeutralContainer class="space-y-2">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span class="font-medium">Übungsabend</span>
            <span class="ml-2 text-sm text-(--text-muted)">Dienstag, 2026-05-19</span>
            <span class="ml-2 text-xs text-(--text-muted)">18:00 – 20:00</span>
          </div>
        </div>
        <div class="flex items-center gap-1">
          <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
        </div>
      </NeutralContainer>
    </div>

    <template v-if="activeRole === 'guardian'">
      <HelpSection :title="t('helpCenter.eventsUpcoming.asMemberManager')">
        <p>{{ t('helpCenter.eventsUpcoming.asMemberManagerText') }}</p>
      </HelpSection>
    </template>

    <HelpSection :title="t('helpCenter.eventsUpcoming.declineTitle')">
      <p>{{ t('helpCenter.eventsUpcoming.declineText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventsUpcoming.tip') }}</HelpTip>
  </HelpArticle>
</template>
