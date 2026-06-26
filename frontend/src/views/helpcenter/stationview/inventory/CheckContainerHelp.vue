/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryCheckContainerHelp.title')" :subtitle="t('helpCenter.inventoryCheckContainerHelp.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryCheckContainerHelp.purposeTitle')">
      <p>
        {{ t('helpCenter.inventoryCheckContainerHelp.purposePart1') }}
      </p>
      <p>
        {{ t('helpCenter.inventoryCheckContainerHelp.purposePart2') }}
      </p>
    </HelpSection>

    <HelpTip>
      {{ t('helpCenter.inventoryCheckContainerHelp.tip') }}
    </HelpTip>
  </HelpArticle>
</template>
