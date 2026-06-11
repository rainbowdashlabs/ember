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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryCheckResult.title')" :subtitle="t('helpCenter.inventoryCheckResult.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryCheckResult.whatShown')">
      <p>{{ t('helpCenter.inventoryCheckResult.whatShownText') }}</p>
    </HelpSection>

    <!-- Dummy: Check result view -->
    <div class="space-y-3">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <SectionHeader>Max Mustermann</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('inventory.check.lastResult') }}</p>
        </div>
        <SecondaryButton :icon="['fas', 'chevron-left']">
          {{ t('inventory.check.backToOverview') }}
        </SecondaryButton>
      </div>

      <NeutralContainer>
        <div class="text-sm text-(--text-muted)">
          10.05.2026, 14:30 &middot; {{ t('inventory.check.checkedBy') }}: Admin User
        </div>
      </NeutralContainer>

      <!-- Dummy: Result cards -->
      <NeutralContainer class="border-l-4 bg-success/10 border-success">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm">Helm <span class="font-normal text-(--text-muted)">[M]</span></div>
            <div class="text-xs text-(--text-muted)">Helme &middot; INV-0001</div>
          </div>
          <SuccessBadge class="self-start">
            {{ t('inventory.check.resultConfirmed') }}
          </SuccessBadge>
        </div>
      </NeutralContainer>

      <NeutralContainer class="border-l-4 bg-error/10 border-error">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm">Jacke <span class="font-normal text-(--text-muted)">[L]</span></div>
            <div class="text-xs text-(--text-muted)">Jacken &middot; INV-0015</div>
          </div>
          <ErrorBadge class="self-start">
            {{ t('inventory.check.resultLost') }}
          </ErrorBadge>
        </div>
        <MutedText tag="p" size="sm" class="mt-1">Konnte nicht gefunden werden</MutedText>
      </NeutralContainer>

      <NeutralContainer class="border-l-4 bg-info/10 border-info">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm">Stiefel <span class="font-normal text-(--text-muted)">[42]</span></div>
            <div class="text-xs text-(--text-muted)">Stiefel &middot; INV-0030</div>
          </div>
          <InfoBadge class="self-start">
            {{ t('inventory.check.resultNotInPossession') }}
          </InfoBadge>
        </div>
      </NeutralContainer>
    </div>

    <HelpTip>{{ t('helpCenter.inventoryCheckResult.tip') }}</HelpTip>
  </HelpArticle>
</template>
