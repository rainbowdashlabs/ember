/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import ProseContent from '@/components/display/ProseContent.vue'

const {t} = useI18n()

const AUTHOR = 'Markus Lehmann'
const PUBLISHED = '28.04.2026 20:05'
const BODY = ref('<p>Wir laden euch herzlich zum gemeinsamen Übungstag am 15. Juni ein.</p>')
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedNewsDetail.title')"
               :subtitle="t('helpCenter.federatedNewsDetail.subtitle')">
    <HelpSection :title="t('helpCenter.federatedNewsDetail.whatIs')">
      <p>{{ t('helpCenter.federatedNewsDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedNewsDetail.howTo')">
      <p>{{ t('helpCenter.federatedNewsDetail.howToStep1') }}</p>
      <p>{{ t('helpCenter.federatedNewsDetail.howToStep2') }}</p>
      <p>{{ t('helpCenter.federatedNewsDetail.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedNewsDetail.exampleTitle')">
      <p>{{ t('helpCenter.federatedNewsDetail.exampleText') }}</p>
      <div class="space-y-4">
        <SecondaryButton :icon="['fas', 'arrow-left']" compact>{{ t('common.back') }}</SecondaryButton>
        <NeutralContainer class="space-y-3">
          <div>
            <SubHeader>{{ t('helpCenter.newsList.federatedTitle') }}</SubHeader>
            <p class="text-xs text-(--text-muted) flex items-center gap-1.5 mt-1">
              <StationBadge :station-name="t('news.partnerNews')"/>
              <span>{{ AUTHOR }} &middot; {{ PUBLISHED }}</span>
            </p>
          </div>
          <ProseContent v-html="BODY"/>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedNewsDetail.editTitle')">
      <p>{{ t('helpCenter.federatedNewsDetail.editText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federatedNewsDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
