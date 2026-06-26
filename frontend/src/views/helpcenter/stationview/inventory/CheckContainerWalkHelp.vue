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
  <HelpArticle
      :title="t('helpCenter.inventoryCheckContainerWalk.title')"
      :subtitle="t('helpCenter.inventoryCheckContainerWalk.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryCheckContainerWalk.flowTitle')">
      <p>{{ t('helpCenter.inventoryCheckContainerWalk.flowText') }}</p>
      <p>{{ t('helpCenter.inventoryCheckContainerWalk.deepText') }}</p>
      <p>{{ t('helpCenter.inventoryCheckContainerWalk.walkText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckContainerWalk.resultTitle')">
      <p>{{ t('helpCenter.inventoryCheckContainerWalk.resultText') }}</p>
      <HelpTip>{{ t('helpCenter.inventoryCheckContainerWalk.tip') }}</HelpTip>
    </HelpSection>
  </HelpArticle>
</template>
