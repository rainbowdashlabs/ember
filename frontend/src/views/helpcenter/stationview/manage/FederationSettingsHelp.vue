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
  <HelpArticle :title="t('helpCenter.federationSettings.title')" :subtitle="t('helpCenter.federationSettings.subtitle')">
    <HelpSection :title="t('helpCenter.federationSettings.visibilityTitle')">
      <p>{{ t('helpCenter.federationSettings.visibilityText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federationSettings.publicKbTitle')">
      <p>{{ t('helpCenter.federationSettings.publicKbText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federationSettings.tip') }}</HelpTip>
  </HelpArticle>
</template>
