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
  <HelpArticle :title="t('helpCenter.stationStorage.title')" :subtitle="t('helpCenter.stationStorage.subtitle')">
    <HelpSection :title="t('helpCenter.stationStorage.overview')">
      <p>{{ t('helpCenter.stationStorage.overviewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationStorage.categoriesTitle')">
      <p>{{ t('helpCenter.stationStorage.categoriesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.stationStorage.tip') }}</HelpTip>
  </HelpArticle>
</template>
