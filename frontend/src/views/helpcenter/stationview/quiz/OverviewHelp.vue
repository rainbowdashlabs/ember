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
import BulletList from '@/components/typography/BulletList.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.overviewTitle')" :subtitle="t('helpCenter.quiz.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.whatIs')">
      <p>{{ t('helpCenter.quiz.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.pagesTitle')">
      <p>{{ t('helpCenter.quiz.pagesText') }}</p>
      <BulletList class="mt-2">
        <li><strong>{{ t('helpCenter.quiz.pageCatalogs') }}:</strong> {{ t('helpCenter.quiz.pageCatalogsDesc') }}</li>
        <li><strong>{{ t('helpCenter.quiz.pageTests') }}:</strong> {{ t('helpCenter.quiz.pageTestsDesc') }}</li>
        <li><strong>{{ t('helpCenter.quiz.pageTraining') }}:</strong> {{ t('helpCenter.quiz.pageTrainingDesc') }}</li>
      </BulletList>
    </HelpSection>

    <!-- Dummy: Overview cards -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
      <NeutralContainer>
        <div class="text-center space-y-2">
          <font-awesome-icon :icon="['fas', 'book']" class="text-2xl text-primary" />
          <p class="font-medium text-sm">{{ t('helpCenter.quiz.pageCatalogs') }}</p>
        </div>
      </NeutralContainer>
      <NeutralContainer>
        <div class="text-center space-y-2">
          <font-awesome-icon :icon="['fas', 'clipboard-check']" class="text-2xl text-primary" />
          <p class="font-medium text-sm">{{ t('helpCenter.quiz.pageTests') }}</p>
        </div>
      </NeutralContainer>
      <NeutralContainer>
        <div class="text-center space-y-2">
          <font-awesome-icon :icon="['fas', 'brain']" class="text-2xl text-primary" />
          <p class="font-medium text-sm">{{ t('helpCenter.quiz.pageTraining') }}</p>
        </div>
      </NeutralContainer>
    </div>

    <HelpTip>{{ t('helpCenter.quiz.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
