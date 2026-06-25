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
import DummyResultView from '@/views/helpcenter/stationview/inventory/checkresulthelp/DummyResultView.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryCheckResult.title')" :subtitle="t('helpCenter.inventoryCheckResult.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryCheckResult.whatShown')">
      <p>{{ t('helpCenter.inventoryCheckResult.whatShownText') }}</p>
    </HelpSection>

    <DummyResultView/>

    <HelpTip>{{ t('helpCenter.inventoryCheckResult.tip') }}</HelpTip>
  </HelpArticle>
</template>
