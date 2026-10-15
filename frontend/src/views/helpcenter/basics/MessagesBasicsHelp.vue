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
import BulletList from '@/components/typography/BulletList.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.messages.title')" :subtitle="t('helpCenter.basics.messages.subtitle')">
    <HelpSection :title="t('helpCenter.basics.messages.whoTitle')">
      <p>{{ t('helpCenter.basics.messages.whoText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.basics.messages.whoStays') }}</li>
        <li>{{ t('helpCenter.basics.messages.whoPartners') }}</li>
        <li>{{ t('helpCenter.basics.messages.whoNames') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.messages.guardianTitle')">
      <p>{{ t('helpCenter.basics.messages.guardianText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.messages.blockTitle')">
      <p>{{ t('helpCenter.basics.messages.blockText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.messages.reportTitle')">
      <p>{{ t('helpCenter.basics.messages.reportText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.messages.storedTitle')">
      <p>{{ t('helpCenter.basics.messages.storedText') }}</p>
      <p>{{ t('helpCenter.basics.messages.deletedText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.messages.tip') }}</HelpTip>
  </HelpArticle>
</template>
