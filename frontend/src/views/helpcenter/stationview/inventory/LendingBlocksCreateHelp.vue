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

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.lendingBlocksCreate.title')" :subtitle="t('helpCenter.lendingBlocksCreate.subtitle')">
    <HelpSection :title="t('helpCenter.lendingBlocksCreate.scopeTitle')">
      <p>{{ t('helpCenter.lendingBlocksCreate.scopeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingBlocksCreate.itemsTitle')">
      <p>{{ t('helpCenter.lendingBlocksCreate.itemsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.lendingBlocksCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
