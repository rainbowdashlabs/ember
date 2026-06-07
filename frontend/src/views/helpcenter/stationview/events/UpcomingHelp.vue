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
import type {HelpPerspective} from '@/components/helpcenter/HelpRoleToggle.vue'
import {StationPermission} from '@/api/types'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const {t} = useI18n()

const perspectives: HelpPerspective[] = [
  {key: 'member', label: t('helpCenter.roles.member'), permissions: [StationPermission.USER]},
  {key: 'guardian', label: t('helpCenter.roles.memberManager'), permissions: [StationPermission.MEMBER_GUARDIAN]},
]
const activeView = ref('')
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventsUpcoming.title')" :subtitle="t('helpCenter.eventsUpcoming.subtitle')">
    <HelpSection :title="t('helpCenter.eventsUpcoming.whatShown')">
      <p>{{ t('helpCenter.eventsUpcoming.whatShownText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeView" :perspectives="perspectives"/>

    <!-- Dummy: Filter bar -->
    <HelpSection :title="t('helpCenter.eventsUpcoming.filterTitle')">
      <p>{{ t('helpCenter.eventsUpcoming.filterText') }}</p>
      <NeutralContainer class="flex flex-wrap items-center gap-3 mt-3">
        <TextInput model-value="" :placeholder="t('helpCenter.eventsUpcoming.filterTitle')" disabled class="flex-1 min-w-48"/>
        <SelectInput model-value="" disabled class="w-40">
          <option value="">{{ t('events.category') }}</option>
        </SelectInput>
        <div class="flex items-center gap-2">
          <ToggleInput :model-value="false"/>
          <span class="text-sm">{{ t('eventsUpcoming.needsAction') }}</span>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Today's events -->
    <HelpSection :title="t('eventsUpcoming.today')">
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
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsUpcoming.registrationTitle')">
      <p>{{ t('helpCenter.eventsUpcoming.registrationText') }}</p>
      <p>{{ t('helpCenter.eventsUpcoming.statusPending') }}</p>
      <p>{{ t('helpCenter.eventsUpcoming.statusAccepted') }}</p>
      <p>{{ t('helpCenter.eventsUpcoming.statusDenied') }}</p>
      <p>{{ t('helpCenter.eventsUpcoming.statusDeclined') }}</p>
    </HelpSection>

    <!-- Dummy: Upcoming event with registration -->
    <HelpSection :title="t('eventsUpcoming.upcoming')">
      <SectionHeader>{{ t('eventsUpcoming.upcoming') }}</SectionHeader>
      <div class="space-y-2">
        <NeutralContainer class="space-y-2">
          <div class="flex items-center justify-between flex-wrap gap-2">
            <div>
              <span class="font-medium">Wettkampf Vorbereitung</span>
              <MutedText size="sm" class="ml-2">Samstag, 25.05.2026</MutedText>
              <MutedText class="ml-2">14:00 – 17:00</MutedText>
            </div>
            <div class="flex items-center gap-2 text-xs">
              <SuccessBadge>5 {{ t('eventsUpcoming.accepted') }}</SuccessBadge>
              <InfoBadge>2 {{ t('eventsUpcoming.pendingCount') }}</InfoBadge>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <PrimaryButton :icon="['fas', 'check']">
              {{ t('eventsUpcoming.register') }}
            </PrimaryButton>
            <ErrorButton :icon="['fas', 'ban']">
              {{ t('eventsUpcoming.decline') }}
            </ErrorButton>
          </div>
        </NeutralContainer>
        <NeutralContainer class="space-y-2">
          <div class="flex items-center justify-between flex-wrap gap-2">
            <div>
              <span class="font-medium">Übungsabend</span>
              <MutedText size="sm" class="ml-2">Dienstag, 19.05.2026</MutedText>
              <MutedText class="ml-2">18:00 – 20:00</MutedText>
            </div>
          </div>
          <div class="flex items-center gap-1">
            <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <!-- Federated events -->
    <HelpSection :title="t('helpCenter.eventsUpcoming.federatedTitle')">
      <p>{{ t('helpCenter.eventsUpcoming.federatedText') }}</p>
    </HelpSection>

    <template v-if="activeView === 'guardian'">
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
