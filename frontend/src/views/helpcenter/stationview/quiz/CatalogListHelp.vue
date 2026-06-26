/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BulletList from '@/components/typography/BulletList.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.catalogListTitle')" :subtitle="t('helpCenter.quiz.catalogListSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.whatIsCatalog')">
      <p>{{ t('helpCenter.quiz.whatIsCatalogText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.catalogListHow')">
      <p>{{ t('helpCenter.quiz.catalogListHowText') }}</p>
    </HelpSection>

    <!-- Dummy: Catalog list -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SubHeader>{{ t('quiz.catalogs.title') }}</SubHeader>
        <PrimaryButton :icon="['fas', 'plus']" disabled>
          {{ t('quiz.createCatalog') }}
        </PrimaryButton>
      </div>

      <NeutralContainer>
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <span class="font-medium">Brandschutz Grundlagen</span>
            <p class="text-xs text-(--text-muted)">Alles rund um den Brandschutz</p>
          </div>
          <div class="flex items-center gap-2">
            <InfoBadge>12 {{ t('quiz.questions.title') }}</InfoBadge>
            <SecondaryButton disabled>{{ t('common.open') }}</SecondaryButton>
          </div>
        </div>
      </NeutralContainer>

      <NeutralContainer>
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <span class="font-medium">Erste Hilfe</span>
            <p class="text-xs text-(--text-muted)">Erste-Hilfe-Wissen abfragen</p>
          </div>
          <div class="flex items-center gap-2">
            <InfoBadge>8 {{ t('quiz.questions.title') }}</InfoBadge>
            <SecondaryButton disabled>{{ t('common.open') }}</SecondaryButton>
          </div>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.quiz.createCatalogTitle')">
      <p>{{ t('helpCenter.quiz.createCatalogText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.quiz.createCatalogName') }}</li>
        <li>{{ t('helpCenter.quiz.createCatalogDesc') }}</li>
        <li>{{ t('helpCenter.quiz.createCatalogTraining') }}</li>
      </BulletList>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quiz.catalogListTip') }}</HelpTip>
  </HelpArticle>
</template>
