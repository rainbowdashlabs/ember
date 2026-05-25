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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.lostAndFound.title')" :subtitle="t('helpCenter.lostAndFound.subtitle')">
    <HelpSection :title="t('helpCenter.lostAndFound.whatIs')">
      <p>{{ t('helpCenter.lostAndFound.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: Lost and found list -->
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('lostAndFound.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']" disabled>
        {{ t('lostAndFound.create') }}
      </PrimaryButton>
    </div>

    <div class="grid gap-3 sm:grid-cols-2">
      <NeutralContainer class="space-y-3">
        <div class="w-full h-28 rounded bg-bg-light-accent dark:bg-bg-dark-accent flex items-center justify-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'image']" class="text-2xl"/>
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">Roter Handschuh (links)</p>
          <p class="text-xs text-(--text-muted)">{{ t('lostAndFound.foundAt') }}: 18.05.2026</p>
          <SuccessBadge>{{ t('lostAndFound.claimedByYou') }}</SuccessBadge>
        </div>
        <div class="flex gap-2">
          <SuccessButton :icon="['fas', 'hand']" class="text-xs flex-1" disabled>
            {{ t('lostAndFound.claim') }}
          </SuccessButton>
          <DeleteButton disabled/>
        </div>
      </NeutralContainer>

      <NeutralContainer class="space-y-3">
        <div class="w-full h-28 rounded bg-bg-light-accent dark:bg-bg-dark-accent flex items-center justify-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'image']" class="text-2xl"/>
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">Blaue Trinkflasche</p>
          <p class="text-xs text-(--text-muted)">{{ t('lostAndFound.foundAt') }}: 15.05.2026</p>
        </div>
        <div class="flex gap-2">
          <SuccessButton :icon="['fas', 'hand']" class="text-xs flex-1" disabled>
            {{ t('lostAndFound.claim') }}
          </SuccessButton>
          <DeleteButton disabled/>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.lostAndFound.reportTitle')">
      <p>{{ t('helpCenter.lostAndFound.reportText') }}</p>
    </HelpSection>

    <!-- Dummy: Create form -->
    <NeutralContainer class="space-y-3">
      <p class="text-sm font-semibold">{{ t('lostAndFound.createTitle') }}</p>
      <div>
        <FieldLabel hint class="mb-1">{{ t('lostAndFound.description') }}</FieldLabel>
        <TextAreaInput :model-value="''" :placeholder="t('lostAndFound.descriptionPlaceholder')" disabled/>
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('lostAndFound.foundAt') }}</FieldLabel>
        <DateInput model-value="" disabled/>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.lostAndFound.claimTitle')">
      <p>{{ t('helpCenter.lostAndFound.claimText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lostAndFound.managerTitle')">
      <p>{{ t('helpCenter.lostAndFound.managerText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.lostAndFound.tip') }}</HelpTip>
  </HelpArticle>
</template>
