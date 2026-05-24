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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import BulletList from '@/components/typography/BulletList.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.kbFileView.title')" :subtitle="t('helpCenter.kbFileView.subtitle')">
    <HelpSection :title="t('helpCenter.kbFileView.whatIs')">
      <p>{{ t('helpCenter.kbFileView.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.typesTitle')">
      <p>{{ t('helpCenter.kbFileView.typesText') }}</p>
    </HelpSection>

    <!-- Dummy: file header -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-start justify-between gap-3">
        <div>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'file-lines']" class="text-[var(--accent)]" />
            <span class="font-semibold text-base">Grundlagen der Ersten Hilfe</span>
            <PrimaryBadge>Markdown</PrimaryBadge>
          </div>
          <MutedText tag="div" class="mt-1">Zuletzt geändert: 12. Mai 2026 · Max Mustermann</MutedText>
        </div>
        <div class="flex gap-2 flex-shrink-0">
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'pen']" class="mr-1" /> Bearbeiten
          </SecondaryButton>
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'clock-rotate-left']" class="mr-1" /> Versionen
          </SecondaryButton>
        </div>
      </div>
      <div class="border-t border-[var(--border)] pt-3 text-sm space-y-2">
        <p class="font-semibold text-base">Was ist Erste Hilfe?</p>
        <p class="text-sm">Erste Hilfe bedeutet, einer verletzten oder erkrankten Person sofort zu helfen — noch bevor der Rettungsdienst eintrifft. Das kann Leben retten.</p>
        <BulletList>
          <li>Notruf absetzen (112)</li>
          <li>Stabile Seitenlage bei Bewusstlosigkeit</li>
          <li>Herzdruckmassage bei Herzstillstand</li>
        </BulletList>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kbFileView.editTitle')">
      <p>{{ t('helpCenter.kbFileView.editText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.versionsTitle')">
      <p>{{ t('helpCenter.kbFileView.versionsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.kbFileView.tip') }}</HelpTip>
  </HelpArticle>
</template>
