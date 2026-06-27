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
import EnvVarsList from './configurationhelp/EnvVarsList.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.configuration.title')" :subtitle="t('helpCenter.basics.configuration.subtitle')">
    <HelpSection :title="t('helpCenter.basics.configuration.fileTitle')">
      <p>{{ t('helpCenter.basics.configuration.fileText') }}</p>
      <p>{{ t('helpCenter.basics.configuration.fileText2') }}</p>
      <BulletList>
        <li><strong>{{ t('helpCenter.basics.configuration.fileDb') }}:</strong> {{ t('helpCenter.basics.configuration.fileDbText') }}</li>
        <li><strong>{{ t('helpCenter.basics.configuration.fileMail') }}:</strong> {{ t('helpCenter.basics.configuration.fileMailText') }}</li>
        <li><strong>{{ t('helpCenter.basics.configuration.fileUrl') }}:</strong> {{ t('helpCenter.basics.configuration.fileUrlText') }}</li>
        <li><strong>{{ t('helpCenter.basics.configuration.fileAuth') }}:</strong> {{ t('helpCenter.basics.configuration.fileAuthText') }}</li>
        <li><strong>{{ t('helpCenter.basics.configuration.fileStorage') }}:</strong> {{ t('helpCenter.basics.configuration.fileStorageText') }}</li>
        <li><strong>{{ t('helpCenter.basics.configuration.fileMetrics') }}:</strong> {{ t('helpCenter.basics.configuration.fileMetricsText') }}</li>
        <li><strong>{{ t('helpCenter.basics.configuration.fileNetwork') }}:</strong> {{ t('helpCenter.basics.configuration.fileNetworkText') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.configuration.envVarsTitle')">
      <p>{{ t('helpCenter.basics.configuration.envVarsText') }}</p>
      <p>{{ t('helpCenter.basics.configuration.envVarsText2') }}</p>
      <EnvVarsList />
      <HelpTip>{{ t('helpCenter.basics.configuration.envTip') }}</HelpTip>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.configuration.tip') }}</HelpTip>
  </HelpArticle>
</template>
