/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryCheckMemberOverviewHelp.title')" :subtitle="t('helpCenter.inventoryCheckMemberOverviewHelp.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryCheckMemberOverviewHelp.overviewTitle')">
      <p>
        {{ t('helpCenter.inventoryCheckMemberOverviewHelp.overviewText') }}
      </p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMemberOverviewHelp.flowTitle')">
      <p>
        {{ t('helpCenter.inventoryCheckMemberOverviewHelp.flowText') }}
      </p>
    </HelpSection>
  </HelpArticle>
</template>
