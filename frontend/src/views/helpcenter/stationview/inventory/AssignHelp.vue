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
  <HelpArticle :title="t('helpCenter.inventoryAssignHelp.title')" :subtitle="t('helpCenter.inventoryAssignHelp.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryAssignHelp.whoCanAssignTitle')">
      <p>
        {{ t('helpCenter.inventoryAssignHelp.whoCanAssignPart1') }}
        <em>{{ t('helpCenter.inventoryAssignHelp.whoCanAssignEm') }}</em>
        {{ t('helpCenter.inventoryAssignHelp.whoCanAssignPart2') }}
      </p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryAssignHelp.flowTitle')">
      <p>
        {{ t('helpCenter.inventoryAssignHelp.flowPart1') }}
      </p>
      <p>
        {{ t('helpCenter.inventoryAssignHelp.flowPart2') }}
      </p>
    </HelpSection>

    <HelpTip>
      {{ t('helpCenter.inventoryAssignHelp.tip') }}
    </HelpTip>
  </HelpArticle>
</template>
