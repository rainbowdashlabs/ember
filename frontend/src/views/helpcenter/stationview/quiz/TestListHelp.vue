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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BulletList from '@/components/typography/BulletList.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.testListTitle')" :subtitle="t('helpCenter.quiz.testListSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.whatIsTest')">
      <p>{{ t('helpCenter.quiz.whatIsTestText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.testListHow')">
      <p>{{ t('helpCenter.quiz.testListHowText') }}</p>
    </HelpSection>

    <!-- Dummy: Test list -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SubHeader>{{ t('quiz.tests') }}</SubHeader>
        <PrimaryButton :icon="['fas', 'plus']" disabled>
          {{ t('quiz.createTest') }}
        </PrimaryButton>
      </div>

      <NeutralContainer>
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Brandschutz-Pruefung</span>
              <SuccessBadge>{{ t('quiz.statusActive') }}</SuccessBadge>
            </div>
            <p class="text-xs text-(--text-muted)">Pruefung zum Thema Brandschutz</p>
          </div>
          <SecondaryButton disabled>{{ t('common.open') }}</SecondaryButton>
        </div>
      </NeutralContainer>

      <NeutralContainer>
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Erste-Hilfe-Test</span>
              <InfoBadge>{{ t('quiz.statusDraft') }}</InfoBadge>
            </div>
            <p class="text-xs text-(--text-muted)">Entwurf fuer den naechsten Uebungsabend</p>
          </div>
          <SecondaryButton disabled>{{ t('common.open') }}</SecondaryButton>
        </div>
      </NeutralContainer>

      <NeutralContainer>
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Knoten-Quiz</span>
              <ErrorBadge>{{ t('quiz.statusClosed') }}</ErrorBadge>
            </div>
            <p class="text-xs text-(--text-muted)">Abgeschlossener Test</p>
          </div>
          <SecondaryButton disabled>{{ t('common.open') }}</SecondaryButton>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.quiz.createTestTitle')">
      <p>{{ t('helpCenter.quiz.createTestText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.quiz.createTestName') }}</li>
        <li>{{ t('helpCenter.quiz.createTestDesc') }}</li>
        <li>{{ t('helpCenter.quiz.createTestTime') }}</li>
        <li>{{ t('helpCenter.quiz.createTestShuffle') }}</li>
      </BulletList>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quiz.testListTip') }}</HelpTip>
  </HelpArticle>
</template>
