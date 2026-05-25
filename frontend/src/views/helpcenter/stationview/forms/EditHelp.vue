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

    <!-- Dummy: question list -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between gap-2 p-3 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">Wie gefällt dir das Training?</div>
          <div class="text-xs text-(--text-muted)">Bewertung (1–5)</div>
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
          <div class="text-sm font-medium">Was hat dir besonders gut gefallen?</div>
          <div class="text-xs text-(--text-muted)">Freitext</div>
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
          <div class="text-sm font-medium">Welche Themen soll das nächste Training behandeln?</div>
          <div class="text-xs text-(--text-muted)">Multiple Choice</div>
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

    <HelpSection :title="t('helpCenter.formsEdit.saveTitle')">
      <p>{{ t('helpCenter.formsEdit.saveText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
