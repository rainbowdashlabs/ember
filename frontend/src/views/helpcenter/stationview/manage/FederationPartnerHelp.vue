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
import {ref} from 'vue'

const {t} = useI18n()

const dummyKbReceive = ref(true)
const dummyKbSend = ref(false)
const dummyQuizReceive = ref(true)
const dummyQuizSend = ref(true)
const dummyProtocolReceive = ref(false)
const dummyProtocolSend = ref(false)
</script>

<template>
  <HelpArticle :title="t('helpCenter.federationPartner.title')" :subtitle="t('helpCenter.federationPartner.subtitle')">
    <HelpSection :title="t('helpCenter.federationPartner.whatIs')">
      <p>{{ t('helpCenter.federationPartner.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: partner header -->
    <NeutralContainer class="space-y-2">
      <div class="flex items-center gap-3">
        <div class="flex-1">
          <div class="font-semibold text-base">JF Musterstadt</div>
          <div class="text-xs text-(--text-muted)">Verbunden seit 1. März 2026 · Protokollversion v1</div>
        </div>
        <SuccessBadge>{{ t('federation.active') }}</SuccessBadge>
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
            <th class="text-center py-2 px-3 font-medium w-32">{{ t('federation.receive') }}</th>
            <th class="text-center py-2 px-3 font-medium w-32">{{ t('federation.send') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-[var(--border)]">
            <td class="py-2 px-3">{{ t('federation.cap.kb') }}</td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyKbReceive" /></td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyKbSend" /></td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <td class="py-2 px-3">{{ t('federation.cap.quiz') }}</td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyQuizReceive" /></td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyQuizSend" /></td>
          </tr>
          <tr>
            <td class="py-2 px-3">{{ t('federation.cap.protocol') }}</td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyProtocolReceive" /></td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyProtocolSend" /></td>
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
