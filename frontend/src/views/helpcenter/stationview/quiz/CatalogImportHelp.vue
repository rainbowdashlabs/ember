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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CatalogFormatReference from '~/views/stationview/quiz/catalogimportview/formatreference/CatalogFormatReference.vue'
import ImportStepsHelp from './catalogimporthelp/ImportStepsHelp.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizCatalogImport.title')" :subtitle="t('helpCenter.quizCatalogImport.subtitle')">
    <HelpSection :title="t('helpCenter.quizCatalogImport.whatIs')">
      <p>{{ t('helpCenter.quizCatalogImport.whatIsText') }}</p>
    </HelpSection>

    <ImportStepsHelp />

    <HelpSection :title="t('helpCenter.quizCatalogImport.formatTitle')">
      <p>{{ t('helpCenter.quizCatalogImport.formatText') }}</p>
      <NeutralContainer>
        <CatalogFormatReference :offer-downloads="false" />
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogImport.rejectedTitle')">
      <p>{{ t('helpCenter.quizCatalogImport.rejectedText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizCatalogImport.tip') }}</HelpTip>
  </HelpArticle>
</template>
