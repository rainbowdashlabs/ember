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
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {ref} from 'vue'

const {t} = useI18n()

const dummyKbImport = ref(true)
const dummyKbExport = ref(false)
const dummyQuizImport = ref(true)
const dummyQuizExport = ref(true)
const dummyProtocolImport = ref(false)
const dummyProtocolExport = ref(false)
const dummyInventoryImport = ref(false)
const dummyInventoryExport = ref(false)
</script>

<template>
  <HelpArticle :title="t('helpCenter.federation.title')" :subtitle="t('helpCenter.federation.subtitle')">
    <HelpSection :title="t('helpCenter.federation.whatIs')">
      <p>{{ t('helpCenter.federation.whatIsText') }}</p>
      <p>{{ t('helpCenter.federation.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federation.partnerListTitle')">
      <p>{{ t('helpCenter.federation.partnerListText') }}</p>
    </HelpSection>

    <!-- Dummy: Partner list -->
    <NeutralContainer>
      <div class="space-y-2">
        <div class="flex items-center gap-3">
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm">JF Musterstadt</div>
            <div class="text-xs text-(--text-muted)">v1</div>
          </div>
          <SuccessBadge>{{ t('federation.active') }}</SuccessBadge>
          <PrimaryButton class="!text-xs !py-1 !px-2" disabled>
            <font-awesome-icon :icon="['fas', 'sliders']" class="mr-1" /> {{ t('federation.manage') }}
          </PrimaryButton>
        </div>
        <div class="flex items-center gap-3">
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm">JF Beispieldorf</div>
            <div class="text-xs text-(--text-muted)">v1</div>
          </div>
          <SecondaryBadge>{{ t('federation.pending') }}</SecondaryBadge>
          <PrimaryButton class="!text-xs !py-1 !px-2" disabled>
            <font-awesome-icon :icon="['fas', 'sliders']" class="mr-1" /> {{ t('federation.manage') }}
          </PrimaryButton>
        </div>
        <div class="flex items-center gap-3">
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm">JF Altenburg</div>
            <div class="text-xs text-(--text-muted)">v1</div>
          </div>
          <ErrorBadge>{{ t('federation.suspended') }}</ErrorBadge>
          <PrimaryButton class="!text-xs !py-1 !px-2" disabled>
            <font-awesome-icon :icon="['fas', 'sliders']" class="mr-1" /> {{ t('federation.manage') }}
          </PrimaryButton>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federation.statusTitle')">
      <ul class="list-disc pl-5 space-y-1 text-sm">
        <li><SuccessBadge>{{ t('federation.active') }}</SuccessBadge> — {{ t('helpCenter.federation.statusActive') }}</li>
        <li><SecondaryBadge>{{ t('federation.pending') }}</SecondaryBadge> — {{ t('helpCenter.federation.statusPending') }}</li>
        <li><ErrorBadge>{{ t('federation.suspended') }}</ErrorBadge> — {{ t('helpCenter.federation.statusSuspended') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federation.addPartnerTitle')">
      <p>{{ t('helpCenter.federation.addPartnerText') }}</p>
      <ol class="list-decimal pl-5 space-y-1 text-sm">
        <li>{{ t('helpCenter.federation.step1') }}</li>
        <li>{{ t('helpCenter.federation.step2') }}</li>
        <li>{{ t('helpCenter.federation.step3') }}</li>
        <li>{{ t('helpCenter.federation.step4') }}</li>
      </ol>
    </HelpSection>

    <!-- Dummy: Invite code -->
    <NeutralContainer>
      <div class="space-y-3">
        <div>
          <span class="text-sm font-medium">{{ t('federation.createInvite') }}</span>
          <p class="text-xs text-(--text-muted)">{{ t('federation.createInviteHint') }}</p>
        </div>
        <div class="p-3 bg-(--bg) rounded font-mono text-lg text-center select-all border border-bg-light-accent dark:border-bg-dark-accent">
          EMBER-A3F2-K7X9
        </div>
        <div class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
          <span class="text-sm font-medium">{{ t('federation.acceptInvite') }}</span>
          <p class="text-xs text-(--text-muted)">{{ t('federation.acceptInviteHint') }}</p>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federation.capabilitiesTitle')">
      <p>{{ t('helpCenter.federation.capabilitiesText') }}</p>
      <p>{{ t('helpCenter.federation.capabilitiesText2') }}</p>
    </HelpSection>

    <!-- Dummy: Capabilities table -->
    <NeutralContainer>
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <th class="text-left py-2 px-3 font-medium">{{ t('federation.feature') }}</th>
            <th class="text-center py-2 px-3 font-medium w-32">{{ t('federation.receive') }}</th>
            <th class="text-center py-2 px-3 font-medium w-32">{{ t('federation.send') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <td class="py-2 px-3">{{ t('federation.cap.kb') }}</td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyKbImport" /></td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyKbExport" /></td>
          </tr>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <td class="py-2 px-3">{{ t('federation.cap.quiz') }}</td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyQuizImport" /></td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyQuizExport" /></td>
          </tr>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <td class="py-2 px-3">{{ t('federation.cap.protocol') }}</td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyProtocolImport" /></td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyProtocolExport" /></td>
          </tr>
          <tr>
            <td class="py-2 px-3">{{ t('federation.cap.inventory') }}</td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyInventoryImport" /></td>
            <td class="py-2 px-3 text-center"><ToggleInput v-model="dummyInventoryExport" /></td>
          </tr>
        </tbody>
      </table>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federation.suspendTitle')">
      <p>{{ t('helpCenter.federation.suspendText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federation.tip') }}</HelpTip>
  </HelpArticle>
</template>
