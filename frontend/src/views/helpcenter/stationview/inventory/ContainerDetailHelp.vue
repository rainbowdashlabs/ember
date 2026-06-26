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
  <HelpArticle :title="t('helpCenter.inventoryContainerDetailHelp.title')" :subtitle="t('helpCenter.inventoryContainerDetailHelp.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryContainerDetailHelp.pageTitle')">
      <p>
        {{ t('helpCenter.inventoryContainerDetailHelp.pageText1') }}
      </p>
      <p>
        {{ t('helpCenter.inventoryContainerDetailHelp.pageText2') }}
      </p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryContainerDetailHelp.editTitle')">
      <p>
        {{ t('helpCenter.inventoryContainerDetailHelp.editText') }}
      </p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryContainerDetailHelp.historyTitle')">
      <p>
        {{ t('helpCenter.inventoryContainerDetailHelp.historyText') }}
      </p>
    </HelpSection>
  </HelpArticle>
</template>
