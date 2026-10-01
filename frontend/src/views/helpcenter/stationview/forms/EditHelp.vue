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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FormPagesHelpSections from '@/views/helpcenter/stationview/forms/builderhelp/FormPagesHelpSections.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsEdit.title')" :subtitle="t('helpCenter.formsEdit.subtitle')">
    <HelpSection :title="t('helpCenter.formsEdit.whatIs')">
      <p>{{ t('helpCenter.formsEdit.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsEdit.questionsTitle')">
      <p>{{ t('helpCenter.formsEdit.questionsText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between gap-2 p-3 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">{{ t('helpCenter.sample.forms.trainingRating') }}</div>
          <div class="text-xs text-(--text-muted)">{{ t('helpCenter.sample.forms.ratingType') }}</div>
        </div>
        <div class="flex items-center gap-2">
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'pen']" />
          </SecondaryButton>
          <DeleteButton disabled />
        </div>
      </div>
      <div class="flex items-center justify-between gap-2 p-3 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">{{ t('helpCenter.sample.forms.likedTraining') }}</div>
          <div class="text-xs text-(--text-muted)">{{ t('helpCenter.sample.forms.freeText') }}</div>
        </div>
        <div class="flex items-center gap-2">
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'pen']" />
          </SecondaryButton>
          <DeleteButton disabled />
        </div>
      </div>
      <div class="flex items-center justify-between gap-2 p-3 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">{{ t('helpCenter.sample.forms.nextTopics') }}</div>
          <div class="text-xs text-(--text-muted)">{{ t('helpCenter.sample.forms.multipleChoice') }}</div>
        </div>
        <div class="flex items-center gap-2">
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'pen']" />
          </SecondaryButton>
          <DeleteButton disabled />
        </div>
      </div>
      <PrimaryButton :icon="['fas', 'plus']" class="!text-xs" disabled>
        {{ t('helpCenter.formsEdit.addQuestion') }}
      </PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.formsEdit.typesTitle')">
      <p>{{ t('helpCenter.formsEdit.typesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsEdit.orderTitle')">
      <p>{{ t('helpCenter.formsEdit.orderText') }}</p>
    </HelpSection>

    <FormPagesHelpSections/>

    <HelpSection :title="t('helpCenter.formsEdit.saveTitle')">
      <p>{{ t('helpCenter.formsEdit.saveText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
