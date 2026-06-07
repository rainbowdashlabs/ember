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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {ref} from 'vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'

const {t} = useI18n()

const dummyKbReceive = ref(true)
const dummyKbSend = ref(false)
const dummyQuizReceive = ref(true)
const dummyQuizSend = ref(true)
const dummyProtocolReceive = ref(false)
const dummyProtocolSend = ref(false)
const dummyEventReceive = ref(true)
const dummyEventSend = ref(false)
const dummyBoardReceive = ref(false)
const dummyBoardSend = ref(false)
const dummyNewsReceive = ref(true)
const dummyNewsSend = ref(true)
const dummyInventoryReceive = ref(false)
const dummyInventorySend = ref(false)
</script>

<template>
  <HelpArticle :title="t('helpCenter.federationPartner.title')" :subtitle="t('helpCenter.federationPartner.subtitle')">
    <HelpSection :title="t('helpCenter.federationPartner.whatIs')">
      <p>{{ t('helpCenter.federationPartner.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: partner header with action buttons -->
    <NeutralContainer class="space-y-2">
      <div class="flex items-center gap-2">
        <div class="flex-1">
          <div class="font-semibold text-base">JF Musterstadt</div>
          <div class="text-xs text-(--text-muted)">Verbunden seit 1. März 2026 · Protokollversion v1</div>
        </div>
        <SuccessBadge>{{ t('federation.active') }}</SuccessBadge>
        <div class="flex gap-2">
          <SecondaryButton class="!text-sm" disabled>{{ t('federation.suspend') }}</SecondaryButton>
          <DeleteButton :label="t('federation.end')" disabled/>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federationPartner.capabilitiesTitle')">
      <p>{{ t('helpCenter.federationPartner.capabilitiesText') }}</p>
    </HelpSection>

    <!-- Dummy: capabilities table -->
    <NeutralContainer>
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-[var(--border)]">
            <th class="text-left py-2 px-3 font-medium">{{ t('federation.feature') }}</th>
            <Th align="center" class="font-medium w-32">{{ t('federation.receive') }}</th>
            <Th align="center" class="font-medium w-32">{{ t('federation.send') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-[var(--border)]">
            <Td>{{ t('federation.cap.kb') }}</Td>
            <Td align="center"><ToggleInput v-model="dummyKbReceive" /></Td>
            <Td align="center"><ToggleInput v-model="dummyKbSend" /></Td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <Td>{{ t('federation.cap.quiz') }}</Td>
            <Td align="center"><ToggleInput v-model="dummyQuizReceive" /></Td>
            <Td align="center"><ToggleInput v-model="dummyQuizSend" /></Td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <Td>{{ t('federation.cap.protocol') }}</Td>
            <Td align="center"><ToggleInput v-model="dummyProtocolReceive" /></Td>
            <Td align="center"><ToggleInput v-model="dummyProtocolSend" /></Td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <Td>{{ t('federation.cap.event') }}</Td>
            <Td align="center"><ToggleInput v-model="dummyEventReceive" /></Td>
            <Td align="center"><ToggleInput v-model="dummyEventSend" /></Td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <Td>{{ t('federation.cap.board') }}</Td>
            <Td align="center"><ToggleInput v-model="dummyBoardReceive" /></Td>
            <Td align="center"><ToggleInput v-model="dummyBoardSend" /></Td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <Td>{{ t('federation.cap.news') }}</Td>
            <Td align="center"><ToggleInput v-model="dummyNewsReceive" /></Td>
            <Td align="center"><ToggleInput v-model="dummyNewsSend" /></Td>
          </tr>
          <tr>
            <Td>{{ t('federation.cap.inventory') }}</Td>
            <Td align="center"><ToggleInput v-model="dummyInventoryReceive" /></Td>
            <Td align="center"><ToggleInput v-model="dummyInventorySend" /></Td>
          </tr>
        </tbody>
      </table>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federationPartner.receiveTitle')">
      <p>{{ t('helpCenter.federationPartner.receiveText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federationPartner.sendTitle')">
      <p>{{ t('helpCenter.federationPartner.sendText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federationPartner.suspendTitle')">
      <p>{{ t('helpCenter.federationPartner.suspendText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federationPartner.tip') }}</HelpTip>
  </HelpArticle>
</template>
