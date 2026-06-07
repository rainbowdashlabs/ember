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
import SuccessButton from '@/components/button/SuccessButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolGrading.title')" :subtitle="t('helpCenter.protocolGrading.subtitle')">
    <HelpSection :title="t('helpCenter.protocolGrading.whatIs')">
      <p>{{ t('helpCenter.protocolGrading.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolGrading.howTo')">
      <p>{{ t('helpCenter.protocolGrading.step1') }}</p>
      <p>{{ t('helpCenter.protocolGrading.step2') }}</p>
      <p>{{ t('helpCenter.protocolGrading.step3') }}</p>
    </HelpSection>

    <!-- Dummy: Header with save and exit -->
    <HelpSection :title="t('helpCenter.protocolGrading.headerTitle')">
      <p>{{ t('helpCenter.protocolGrading.headerText') }}</p>
      <div class="flex items-center justify-between mb-2">
        <SectionHeader>Lena Mueller</SectionHeader>
        <SecondaryButton disabled>
          <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1" /> {{ t('protocol.saveAndExit') }}
        </SecondaryButton>
      </div>
    </HelpSection>

    <!-- Dummy: Section navigation tabs -->
    <HelpSection :title="t('helpCenter.protocolGrading.sectionsTitle')">
      <p>{{ t('helpCenter.protocolGrading.sectionsText') }}</p>
      <div class="flex flex-wrap gap-1.5 mb-4">
        <SelectionToggleButton :selected="true" disabled>
          <font-awesome-icon :icon="['fas', 'circle-check']" class="w-3 h-3 text-[var(--success)] mr-1" />
          Notruf
          <span class="ml-1 font-mono">7/8</span>
        </SelectionToggleButton>
        <SelectionToggleButton :selected="false" disabled>
          Knoten
          <span class="ml-1 font-mono">0/11</span>
        </SelectionToggleButton>
        <SelectionToggleButton :selected="false" disabled>
          Schlauche
          <span class="ml-1 font-mono">0/15</span>
        </SelectionToggleButton>
      </div>

      <!-- Total score -->
      <div class="flex items-center justify-between text-sm mb-4">
        <span class="text-[var(--text-muted)]">{{ t('protocol.totalScore') }}:</span>
        <span class="font-mono font-bold text-lg">7 / 34P</span>
      </div>
    </HelpSection>

    <!-- Dummy: Grading view with checkboxes -->
    <HelpSection :title="t('helpCenter.protocolGrading.checkTitle')">
      <p>{{ t('helpCenter.protocolGrading.checkText') }}</p>
      <NeutralContainer class="space-y-3">
        <div class="flex items-center justify-between">
          <SectionHeader class="font-bold">
            <font-awesome-icon :icon="['fas', 'circle-check']" class="w-4 h-4 text-[var(--success)] mr-1" />
            Notruf
          </SectionHeader>
          <SuccessBadge>7 / 8P</SuccessBadge>
        </div>

        <div class="flex items-center gap-2 p-3 rounded-lg border-2 border-[var(--success)]" style="background: color-mix(in srgb, var(--success) 10%, transparent)">
          <font-awesome-icon :icon="['fas', 'square-check']" class="w-6 h-6 text-[var(--success)]" />
          <span class="flex-1 text-sm">5 W-Fragen nennen</span>
          <span class="text-xs text-[var(--text-muted)] font-mono">5P</span>
        </div>
        <div class="flex items-center gap-2 p-3 rounded-lg border-2 border-[var(--success)]" style="background: color-mix(in srgb, var(--success) 10%, transparent)">
          <font-awesome-icon :icon="['fas', 'square-check']" class="w-6 h-6 text-[var(--success)]" />
          <span class="flex-1 text-sm">Notrufnummer 112</span>
          <span class="text-xs text-[var(--text-muted)] font-mono">2P</span>
        </div>
        <div class="flex items-center gap-2 p-3 rounded-lg border-2 border-[var(--border)]">
          <font-awesome-icon :icon="['fas', 'square']" class="w-6 h-6 text-[var(--text-muted)]" />
          <span class="flex-1 text-sm">Notrufnummer 110</span>
          <span class="text-xs text-[var(--text-muted)] font-mono">1P</span>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Navigation buttons -->
    <HelpSection :title="t('helpCenter.protocolGrading.navigationTitle')">
      <p>{{ t('helpCenter.protocolGrading.navigationText') }}</p>
      <div class="space-y-2">
        <div class="flex items-center gap-2">
          <SuccessButton disabled>
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndNext') }}
          </SuccessButton>
          <SuccessButton disabled>
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndExit') }}
          </SuccessButton>
        </div>
        <div class="flex items-center gap-2">
          <SecondaryButton disabled>
            <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" /> {{ t('protocol.prevSection') }}
          </SecondaryButton>
          <div class="flex-1" />
          <PrimaryButton disabled>
            {{ t('protocol.nextSection') }} <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1" />
          </PrimaryButton>
        </div>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolGrading.lockTitle')">
      <p>{{ t('helpCenter.protocolGrading.lockText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolGrading.tip') }}</HelpTip>
  </HelpArticle>
</template>
