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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import CatalogFormatReference from '~/views/stationview/quiz/catalogimportview/formatreference/CatalogFormatReference.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle
      :title="t('helpCenter.quizCatalogCreateImport.title')"
      :subtitle="t('helpCenter.quizCatalogCreateImport.subtitle')"
  >
    <HelpSection :title="t('helpCenter.quizCatalogCreateImport.whatIs')">
      <p>{{ t('helpCenter.quizCatalogCreateImport.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogCreateImport.formatTitle')">
      <NeutralContainer class="space-y-2">
        <div class="flex items-start gap-3">
          <font-awesome-icon :icon="['fas', 'file-code']" class="text-lg mt-0.5" />
          <p class="text-sm">{{ t('helpCenter.quizCatalogCreateImport.formatJson') }}</p>
        </div>
        <div class="flex items-start gap-3">
          <font-awesome-icon :icon="['fas', 'file-csv']" class="text-lg mt-0.5" />
          <p class="text-sm">{{ t('helpCenter.quizCatalogCreateImport.formatCsv') }}</p>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogCreateImport.referenceTitle')">
      <p>{{ t('helpCenter.quizCatalogCreateImport.referenceText') }}</p>
      <NeutralContainer>
        <CatalogFormatReference :offer-downloads="false" />
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogCreateImport.previewTitle')">
      <p>{{ t('helpCenter.quizCatalogCreateImport.previewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogCreateImport.rejectedTitle')">
      <p>{{ t('helpCenter.quizCatalogCreateImport.rejectedText') }}</p>
      <NeutralContainer class="space-y-1">
        <SectionHeader>{{ t('quiz.catalogs.importRejected') }}</SectionHeader>
        <MutedText class="font-mono text-xs">questions[4]: Die Antworten passen nicht zu einer Frage vom Typ ORDERING</MutedText>
        <MutedText class="font-mono text-xs">questions[9]: SORTIEREN ist kein Fragetyp, den Ember kennt</MutedText>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizCatalogCreateImport.tip') }}</HelpTip>
  </HelpArticle>
</template>
