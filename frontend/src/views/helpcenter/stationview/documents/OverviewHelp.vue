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
import HelpFeatureItem from '@/components/helpcenter/HelpFeatureItem.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The documents area and its first page, the reader's own documents: what each page of the area is
 * for, and how a member creates a document for themselves or a child.
 */
const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.documentsOverview.title')" :subtitle="t('helpCenter.documentsOverview.subtitle')">
    <HelpSection :title="t('helpCenter.documentsOverview.whatIs')">
      <p>{{ t('helpCenter.documentsOverview.whatIsText') }}</p>
      <p>{{ t('helpCenter.documentsOverview.profileText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentsOverview.createTitle')">
      <p>{{ t('helpCenter.documentsOverview.createText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentsOverview.exampleTitle')">
      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('documentTemplates.selfServiceCreate') }}</SubHeader>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span class="font-semibold">{{ t('helpCenter.documentsOverview.exampleOffer') }}</span>
          <SecondaryButton :icon="['fas', 'file-circle-plus']">{{ t('common.create') }}</SecondaryButton>
        </div>
        <MutedText size="sm" tag="p">{{ t('documentTemplates.availableFrom', {date: '01.11.2026'}) }}</MutedText>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentsOverview.pagesTitle')">
      <div class="space-y-2">
        <HelpFeatureItem :icon="['fas', 'file']" :title="t('sidebar.ownDocuments')"
                         :description="t('helpCenter.documentsOverview.pageOwn')"/>
        <HelpFeatureItem :icon="['fas', 'box-archive']" :title="t('sidebar.documentStore')"
                         :description="t('helpCenter.documentsOverview.pageStore')"/>
        <HelpFeatureItem :icon="['fas', 'file-signature']" :title="t('sidebar.documentTemplates')"
                         :description="t('helpCenter.documentsOverview.pageTemplates')"/>
        <HelpFeatureItem :icon="['fas', 'clock-rotate-left']" :title="t('sidebar.generatedDocuments')"
                         :description="t('helpCenter.documentsOverview.pageGenerated')"/>
        <HelpFeatureItem :icon="['fas', 'font']" :title="t('sidebar.documentFonts')"
                         :description="t('helpCenter.documentsOverview.pageFonts')"/>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.documentsOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
