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
import DeleteButton from '@/components/button/DeleteButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryProcurement.title')" :subtitle="t('helpCenter.inventoryProcurement.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryProcurement.whatIs')">
      <p>{{ t('helpCenter.inventoryProcurement.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryProcurement.createTitle')">
      <p>{{ t('helpCenter.inventoryProcurement.createInventory') }}</p>
      <p>{{ t('helpCenter.inventoryProcurement.createMember') }}</p>
      <p>{{ t('helpCenter.inventoryProcurement.createSize') }}</p>
      <p>{{ t('helpCenter.inventoryProcurement.createNotes') }}</p>
    </HelpSection>

    <!-- Dummy: Procurement list -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('procurement.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('procurement.create') }}
        </PrimaryButton>
      </div>

      <NeutralContainer>
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div class="space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">Helme</span>
              <span class="text-sm text-(--text-muted)">(L)</span>
              <ErrorBadge>{{ t('procurement.open') }}</ErrorBadge>
            </div>
            <div class="text-sm text-(--text-muted)">Max Mustermann &mdash; 10.05.2026</div>
            <div class="text-sm">Alter Helm gerissen</div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <PrimaryButton :icon="['fas', 'check']">
              {{ t('procurement.markFulfilled') }}
            </PrimaryButton>
            <DeleteButton />
          </div>
        </div>
      </NeutralContainer>

      <NeutralContainer>
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div class="space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">Jacken</span>
              <span class="text-sm text-(--text-muted)">(M)</span>
              <SuccessBadge>{{ t('procurement.fulfilled') }}</SuccessBadge>
            </div>
            <div class="text-sm text-(--text-muted)">Erika Musterfrau &mdash; 01.05.2026</div>
            <div class="text-xs text-(--text-muted)">{{ t('procurement.fulfilledAt') }}: 08.05.2026</div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <DeleteButton />
          </div>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.inventoryProcurement.fulfilledTitle')">
      <p>{{ t('helpCenter.inventoryProcurement.fulfilledText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryProcurement.tip') }}</HelpTip>
  </HelpArticle>
</template>
