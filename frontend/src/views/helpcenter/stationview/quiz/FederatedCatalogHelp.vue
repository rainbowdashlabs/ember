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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedQuizCatalog.title')"
               :subtitle="t('helpCenter.federatedQuizCatalog.subtitle')">
    <HelpSection :title="t('helpCenter.federatedQuizCatalog.whatIs')">
      <p>{{ t('helpCenter.federatedQuizCatalog.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedQuizCatalog.howTo')">
      <p>{{ t('helpCenter.federatedQuizCatalog.howToStep1') }}</p>
      <p>{{ t('helpCenter.federatedQuizCatalog.howToStep2') }}</p>

      <!-- Dummy: shared catalog row -->
      <NeutralContainer class="mt-4">
        <div class="flex items-center justify-between gap-4">
          <div class="min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">Grundlagen Atemschutz</span>
              <StationBadge station-name="Wache Nordstadt"/>
            </div>
            <MutedText tag="p" size="sm">42 Fragen in 5 Kategorien</MutedText>
          </div>
          <PrimaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'copy']"/>
            {{ t('federation.copyToStation') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedQuizCatalog.editTitle')">
      <p>{{ t('helpCenter.federatedQuizCatalog.editText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federatedQuizCatalog.tip') }}</HelpTip>
  </HelpArticle>
</template>
