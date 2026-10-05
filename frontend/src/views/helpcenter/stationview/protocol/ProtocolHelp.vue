/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import { StationPermission } from '@/api/generated/schema'
import GradingItemButton from '@/views/stationview/protocol/gradingview/GradingItemButton.vue'
import ProtocolListExampleSection from './protocolhelp/ProtocolListExampleSection.vue'
import { demoProtocolItems } from './fixtures'

const { t } = useI18n()
const gradingSample = demoProtocolItems(t).slice(1, 3)
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocol.title')" :subtitle="t('helpCenter.protocol.subtitle')">
    <HelpSection :title="t('helpCenter.protocol.whatIs')">
      <p>{{ t('helpCenter.protocol.whatIsText') }}</p>
    </HelpSection>

    <ProtocolListExampleSection />

    <HelpPermissionGuard :permissions="[StationPermission.PROTOCOL_SHARE]"
                         :label="t('helpCenter.permissionLabel.protocolShare')">
      <HelpSection :title="t('helpCenter.protocol.shareTitle')">
        <p>{{ t('helpCenter.protocol.shareText') }}</p>
        <p>{{ t('helpCenter.protocol.shareBadgeText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.protocol.structureTitle')">
      <p>{{ t('helpCenter.protocol.structureText') }}</p>
      <NeutralContainer>
        <div class="space-y-2 text-sm">
          <div class="font-medium">{{ t('helpCenter.sample.protocol.jugendflamme1') }}</div>
          <div class="ml-4 space-y-1">
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--color-primary)]" />
              <span>{{ t('helpCenter.sample.protocol.emergencyCall') }}</span>
              <MutedText class="ml-auto">8P</MutedText>
            </div>
            <div class="ml-6 space-y-0.5 text-xs text-[var(--text-muted)]">
              <div>{{ t('helpCenter.sample.protocol.fiveWPoints') }}</div>
              <div>{{ t('helpCenter.sample.protocol.numbersPoints') }}</div>
            </div>
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--color-primary)]" />
              <span>{{ t('helpCenter.sample.protocol.knots') }}</span>
              <MutedText class="ml-auto">10P</MutedText>
            </div>
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--color-primary)]" />
              <span>{{ t('helpCenter.sample.protocol.hoses') }}</span>
              <MutedText class="ml-auto">15P</MutedText>
            </div>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocol.runsTitle')">
      <p>{{ t('helpCenter.protocol.runsText') }}</p>
      <p>{{ t('helpCenter.protocol.examinersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocol.gradingTitle')">
      <p>{{ t('helpCenter.protocol.gradingText') }}</p>
      <NeutralContainer class="space-y-2">
        <GradingItemButton v-for="(item, index) in gradingSample" :key="item.id" :item="item" :checked="index === 0" />
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocol.lockingTitle')">
      <p>{{ t('helpCenter.protocol.lockingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocol.exportTitle')">
      <p>{{ t('helpCenter.protocol.exportText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocol.tip') }}</HelpTip>
  </HelpArticle>
</template>
