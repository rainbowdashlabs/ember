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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryMember.title')" :subtitle="t('helpCenter.inventoryMember.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryMember.whatShown')">
      <p>{{ t('helpCenter.inventoryMember.whatShownText') }}</p>
    </HelpSection>

    <!-- Dummy: Member inventory view -->
    <div class="space-y-6">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('profile.inventory') }} &mdash; Max Mustermann</SectionHeader>
        <SecondaryButton :icon="['fas', 'chevron-left']">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <!-- Dummy: Inventory group - Helme -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Helme</SubHeader>
          <span class="text-sm text-(--text-muted)">2</span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <NeutralContainer>
            <div class="flex items-start justify-between gap-2">
              <div>
                <div class="font-medium text-sm">Helm <span class="font-normal text-(--text-muted)">[M]</span></div>
                <div class="text-xs text-(--text-muted)">INV-0001</div>
              </div>
              <IconButton :icon="['fas', 'rotate']" :label="t('profile.requestExchange')" class="text-(--text-muted) hover:text-primary" />
            </div>
          </NeutralContainer>
          <NeutralContainer class="opacity-60 border-error">
            <div class="flex items-start justify-between gap-2">
              <div>
                <div class="font-medium text-sm">Helm <span class="font-normal text-(--text-muted)">[S]</span></div>
                <div class="text-xs text-(--text-muted)">INV-0003</div>
                <ErrorBadge class="mt-1">{{ t('profile.lostSince') }} 01.05.2026</ErrorBadge>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </div>

      <!-- Dummy: Inventory group - Jacken -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Jacken</SubHeader>
          <span class="text-sm text-(--text-muted)">1</span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <NeutralContainer>
            <div class="flex items-start justify-between gap-2">
              <div>
                <div class="font-medium text-sm">Jacke <span class="font-normal text-(--text-muted)">[L]</span></div>
                <div class="text-xs text-(--text-muted)">INV-0015</div>
                <InfoBadge class="mt-1">{{ t('exchanges.status.ANNOUNCED') }}</InfoBadge>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </div>
    </div>

    <HelpTip>{{ t('helpCenter.inventoryMember.tip') }}</HelpTip>
  </HelpArticle>
</template>
