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
import SelectInput from '@/components/input/select/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizCatalogImport.title')" :subtitle="t('helpCenter.quizCatalogImport.subtitle')">
    <HelpSection :title="t('helpCenter.quizCatalogImport.whatIs')">
      <p>{{ t('helpCenter.quizCatalogImport.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogImport.formatTitle')">
      <p>{{ t('helpCenter.quizCatalogImport.formatText') }}</p>
      <p>{{ t('helpCenter.quizCatalogImport.formatFlexible') }}</p>
    </HelpSection>

    <!-- Step 1: File upload -->
    <HelpSection :title="t('helpCenter.quizCatalogImport.step1Title')">
      <p>{{ t('helpCenter.quizCatalogImport.step1Text') }}</p>
      <NeutralContainer class="space-y-3">
        <SectionHeader>1. {{ t('helpCenter.quizCatalogImport.uploadLabel') }}</SectionHeader>
        <div class="border-2 border-dashed border-bg-light-accent dark:border-bg-dark-accent rounded-lg p-6 text-center">
          <font-awesome-icon :icon="['fas', 'file-csv']" class="text-3xl text-(--text-muted) mb-2" />
          <p class="text-sm text-(--text-muted)">{{ t('helpCenter.quizCatalogImport.dropHint') }}</p>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Step 2: Column mapping -->
    <HelpSection :title="t('helpCenter.quizCatalogImport.step2Title')">
      <p>{{ t('helpCenter.quizCatalogImport.step2Text') }}</p>
      <NeutralContainer class="space-y-3">
        <SubHeader>2. {{ t('helpCenter.quizCatalogImport.mappingLabel') }}</SubHeader>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div class="space-y-1">
            <FieldLabel>{{ t('helpCenter.quizCatalogImport.colQuestion') }}</FieldLabel>
            <SelectInput :model-value="'Frage'" disabled>
              <option>Frage</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('helpCenter.quizCatalogImport.colAnswer') }}</FieldLabel>
            <SelectInput :model-value="'Antwort'" disabled>
              <option>Antwort</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('helpCenter.quizCatalogImport.colCategory') }}</FieldLabel>
            <SelectInput :model-value="''" disabled>
              <option value="">—</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('helpCenter.quizCatalogImport.colType') }}</FieldLabel>
            <SelectInput :model-value="''" disabled>
              <option value="">—</option>
            </SelectInput>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Step 3: Preview -->
    <HelpSection :title="t('helpCenter.quizCatalogImport.step3Title')">
      <p>{{ t('helpCenter.quizCatalogImport.step3Text') }}</p>
      <NeutralContainer class="space-y-3">
        <div class="flex items-center justify-between">
          <SectionHeader>3. {{ t('helpCenter.quizCatalogImport.previewLabel') }} (3 / 3)</SectionHeader>
          <PrimaryButton disabled>
            <font-awesome-icon :icon="['fas', 'file-import']" class="mr-1" />
            {{ t('helpCenter.quizCatalogImport.importButton') }} (3)
          </PrimaryButton>
        </div>
        <NeutralContainer v-for="q in [
          { title: 'Welche Notrufnummer waehlt man bei Feuer?', type: 'Multiple Choice' },
          { title: 'Wasser leitet Strom.', type: 'Wahr/Falsch' },
          { title: 'Was bedeutet ABC?', type: 'Freitext' },
        ]" :key="q.title">
          <div class="flex items-center justify-between text-sm">
            <div>
              <span class="font-medium">{{ q.title }}</span>
              <span class="text-xs text-(--text-muted) ml-2">{{ q.type }}</span>
            </div>
          </div>
        </NeutralContainer>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizCatalogImport.tip') }}</HelpTip>
  </HelpArticle>
</template>
