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
import type {ReturnState} from '@/api/generated/schema'
import ReturnCheckList from '@/views/stationview/inventory/lendingrequestview/ReturnCheckList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'

const {t} = useI18n()

const samplePieces = [
  {itemId: 1, name: 'Rettungsring', internalId: 'RR-01'},
  {itemId: 2, name: 'Rettungsring', internalId: 'RR-02'},
  {itemId: 3, name: 'Rettungsring', internalId: 'RR-03'},
]
const sampleStates = new Map<number, ReturnState>([[1, 'OK'], [2, 'DAMAGED'], [3, 'MISSING']])
const sampleComments = new Map<number, string>([[2, t('helpCenter.inventoryLendingRequest.sampleDamage')]])
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

    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('helpCenter.inventoryLendingRequest.dummyTitle') }}</SectionHeader>
        <InfoBadge>{{ t('helpCenter.inventoryLendingRequest.statusApproved') }}</InfoBadge>
      </div>
      <div class="grid sm:grid-cols-2 gap-3 text-sm">
        <NeutralContainer>
          <p class="text-[var(--text-muted)] text-xs mb-1">{{ t('helpCenter.inventoryLendingRequest.colStation') }}</p>
          <p class="font-medium">{{ t('helpCenter.sample.stations.dlrgMusterstadt') }}</p>
        </NeutralContainer>
        <NeutralContainer>
          <p class="text-[var(--text-muted)] text-xs mb-1">{{ t('helpCenter.inventoryLendingRequest.colItem') }}</p>
          <p class="font-medium">{{ t('helpCenter.sample.equipment.lifebuoysThree') }}</p>
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
      <ButtonRow pair>
        <PrimaryButton :icon="['fas', 'clipboard-check']">{{ t('lendingReturn.start') }}</PrimaryButton>
        <SecondaryButton :icon="['fas', 'bell']">{{ t('lendingReturn.remind') }}</SecondaryButton>
      </ButtonRow>
      <NeutralContainer class="space-y-2">
        <p class="text-xs text-[var(--text-muted)] font-medium uppercase tracking-wide">{{ t('helpCenter.inventoryLendingRequest.chatTitle') }}</p>
        <div class="flex flex-col gap-2">
          <div class="self-start bg-[var(--bg-accent)] rounded-lg px-3 py-2 text-sm max-w-xs">
            <p class="text-xs text-[var(--text-muted)] mb-0.5">{{ t('helpCenter.sample.stations.dlrgMusterstadt') }}</p>
            <p>{{ t('helpCenter.sample.inventory.lendingQuestion') }}</p>
          </div>
          <div class="self-end bg-primary text-primary-text rounded-lg px-3 py-2 text-sm max-w-xs">
            <p>{{ t('helpCenter.sample.inventory.lendingAnswer') }}</p>
          </div>
          <SuccessBadge class="self-start">{{ t('helpCenter.inventoryLendingRequest.statusReturned') }}</SuccessBadge>
        </div>
      </NeutralContainer>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.inventoryLendingRequest.returnTitle')">
      <p>{{ t('helpCenter.inventoryLendingRequest.returnText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingRequest.returnMissing') }}</p>
      <NeutralContainer>
        <ReturnCheckList :pieces="samplePieces" :states="sampleStates" :comments="sampleComments"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingRequest.remindTitle')">
      <p>{{ t('helpCenter.inventoryLendingRequest.remindText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingRequest.againTitle')">
      <p>{{ t('helpCenter.inventoryLendingRequest.againText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingRequest.tip') }}</HelpTip>
  </HelpArticle>
</template>
