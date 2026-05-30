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
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.requirements.title')" :subtitle="t('helpCenter.requirements.subtitle')">
    <HelpSection :title="t('helpCenter.requirements.whatIs')">
      <p>{{ t('helpCenter.requirements.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.requirements.types')">
      <p>{{ t('helpCenter.requirements.profileText') }}</p>
      <p>{{ t('helpCenter.requirements.formsText') }}</p>
      <p>{{ t('helpCenter.requirements.quizzesText') }}</p>
    </HelpSection>

    <!-- Dummy: requirement items -->
    <NeutralContainer>
      <div class="flex items-center gap-3">
        <font-awesome-icon :icon="['fas', 'user']" class="text-primary text-xl"/>
        <div class="flex-1">
          <SubHeader>{{ t('requirements.profileTitle') }}</SubHeader>
          <p class="text-sm text-(--text-muted)">{{ t('requirements.profileText') }}</p>
        </div>
        <PrimaryButton>{{ t('requirements.fillOut') }}</PrimaryButton>
      </div>
    </NeutralContainer>

    <NeutralContainer>
      <div class="flex items-center gap-3">
        <font-awesome-icon :icon="['fas', 'square-poll-vertical']" class="text-primary text-xl"/>
        <div class="flex-1">
          <SubHeader>Sicherheitsunterweisung</SubHeader>
          <p class="text-sm text-(--text-muted)">{{ t('requirements.formText') }}</p>
        </div>
        <PrimaryButton>{{ t('requirements.fillOut') }}</PrimaryButton>
      </div>
    </NeutralContainer>

    <!-- Dummy: all done -->
    <SuccessContainer>
      <div class="flex items-center gap-3">
        <font-awesome-icon :icon="['fas', 'circle-check']" class="text-xl"/>
        <p>{{ t('requirements.allDone') }}</p>
      </div>
    </SuccessContainer>

    <HelpTip>{{ t('helpCenter.requirements.tip') }}</HelpTip>
  </HelpArticle>
</template>
