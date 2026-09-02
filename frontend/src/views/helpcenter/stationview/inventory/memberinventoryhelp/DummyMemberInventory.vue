/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DummyInventoryGroup from '@/views/helpcenter/stationview/inventory/memberinventoryhelp/DummyInventoryGroup.vue'
import DummyItemCard from '@/views/helpcenter/stationview/inventory/memberinventoryhelp/DummyItemCard.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('profile.inventory') }}: Max Mustermann</SectionHeader>
      <SecondaryButton :icon="['fas', 'chevron-left']">
        {{ t('common.back') }}
      </SecondaryButton>
    </div>

    <DummyInventoryGroup title="Helme" :count="2">
      <DummyItemCard name="Helm" size="M" inventory-id="INV-0001" show-exchange-icon :exchange-label="t('profile.requestExchange')"/>
      <DummyItemCard name="Helm" size="S" inventory-id="INV-0003" faded error-border>
        <ErrorBadge class="mt-1">{{ t('profile.lostSince') }} 01.05.2026</ErrorBadge>
      </DummyItemCard>
    </DummyInventoryGroup>

    <DummyInventoryGroup title="Jacken" :count="1">
      <DummyItemCard name="Jacke" size="L" inventory-id="INV-0015">
        <InfoBadge class="mt-1">{{ t('exchanges.status.ANNOUNCED') }}</InfoBadge>
      </DummyItemCard>
    </DummyInventoryGroup>
  </div>
</template>
