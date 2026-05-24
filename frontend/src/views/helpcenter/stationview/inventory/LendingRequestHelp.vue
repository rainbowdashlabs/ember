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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingRequest.title')" :subtitle="t('helpCenter.inventoryLendingRequest.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingRequest.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingRequest.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingRequest.actionsTitle')">
      <p>{{ t('helpCenter.inventoryLendingRequest.actionApprove') }}</p>
      <p>{{ t('helpCenter.inventoryLendingRequest.actionDecline') }}</p>
      <p>{{ t('helpCenter.inventoryLendingRequest.actionLent') }}</p>
      <p>{{ t('helpCenter.inventoryLendingRequest.actionReturned') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingRequest.chatTitle')">
      <p>{{ t('helpCenter.inventoryLendingRequest.chatText') }}</p>
    </HelpSection>

    <!-- Dummy: Request detail -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('helpCenter.inventoryLendingRequest.dummyTitle') }}</SectionHeader>
        <InfoBadge>{{ t('helpCenter.inventoryLendingRequest.statusApproved') }}</InfoBadge>
      </div>
      <div class="grid sm:grid-cols-2 gap-3 text-sm">
        <NeutralContainer>
          <p class="text-[var(--text-muted)] text-xs mb-1">{{ t('helpCenter.inventoryLendingRequest.colStation') }}</p>
          <p class="font-medium">DLRG Musterstadt</p>
        </NeutralContainer>
        <NeutralContainer>
          <p class="text-[var(--text-muted)] text-xs mb-1">{{ t('helpCenter.inventoryLendingRequest.colItem') }}</p>
          <p class="font-medium">Rettungsring (3×)</p>
        </NeutralContainer>
        <NeutralContainer>
          <p class="text-[var(--text-muted)] text-xs mb-1">{{ t('helpCenter.inventoryLendingRequest.colFrom') }}</p>
          <p class="font-medium">01.06.2026</p>
        </NeutralContainer>
        <NeutralContainer>
          <p class="text-[var(--text-muted)] text-xs mb-1">{{ t('helpCenter.inventoryLendingRequest.colTo') }}</p>
          <p class="font-medium">15.06.2026</p>
        </NeutralContainer>
      </div>
      <div class="flex flex-wrap gap-2">
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
          {{ t('helpCenter.inventoryLendingRequest.dummyMarkLent') }}
        </PrimaryButton>
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'rotate-left']" class="mr-1" />
          {{ t('helpCenter.inventoryLendingRequest.dummyMarkReturned') }}
        </SecondaryButton>
      </div>
      <!-- Chat preview -->
      <NeutralContainer class="space-y-2">
        <p class="text-xs text-[var(--text-muted)] font-medium uppercase tracking-wide">{{ t('helpCenter.inventoryLendingRequest.chatTitle') }}</p>
        <div class="flex flex-col gap-2">
          <div class="self-start bg-[var(--bg-accent)] rounded-lg px-3 py-2 text-sm max-w-xs">
            <p class="text-xs text-[var(--text-muted)] mb-0.5">DLRG Musterstadt</p>
            <p>Werden die Ringe in gutem Zustand zurückgegeben?</p>
          </div>
          <div class="self-end bg-[var(--accent)] text-white rounded-lg px-3 py-2 text-sm max-w-xs">
            <p>Ja, wir passen gut auf die Ausrüstung auf.</p>
          </div>
          <SuccessBadge class="self-start">{{ t('helpCenter.inventoryLendingRequest.statusReturned') }}</SuccessBadge>
        </div>
      </NeutralContainer>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryLendingRequest.tip') }}</HelpTip>
  </HelpArticle>
</template>
