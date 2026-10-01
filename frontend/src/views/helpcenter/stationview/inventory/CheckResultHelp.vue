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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckResultMeta from '@/views/stationview/inventory/checkresultview/CheckResultMeta.vue'
import CheckResultItemCard from '@/views/stationview/inventory/checkresultview/CheckResultItemCard.vue'
import {detail} from './checkresulthelp/fixtures'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryCheckResult.title')" :subtitle="t('helpCenter.inventoryCheckResult.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryCheckResult.whatShown')">
      <p>{{ t('helpCenter.inventoryCheckResult.whatShownText') }}</p>
      <div class="space-y-3">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <p class="text-sm text-(--text-muted)">{{ t('inventory.check.lastResult') }}</p>
          <SecondaryButton :icon="['fas', 'chevron-left']">{{ t('inventory.check.backToOverview') }}</SecondaryButton>
        </div>
        <CheckResultMeta :detail="detail"/>
        <div class="space-y-2">
          <CheckResultItemCard v-for="item in detail.items" :key="item.id" :item="item"/>
        </div>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryCheckResult.tip') }}</HelpTip>
  </HelpArticle>
</template>
