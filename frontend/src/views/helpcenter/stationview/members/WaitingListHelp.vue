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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import CodeSnippet from '@/components/display/CodeSnippet.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.waitingList.title')" :subtitle="t('helpCenter.waitingList.subtitle')">
    <HelpSection :title="t('helpCenter.waitingList.whatIs')">
      <p>{{ t('helpCenter.waitingList.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.waitingList.howTo')">
      <p>{{ t('helpCenter.waitingList.step1') }}</p>
      <p>{{ t('helpCenter.waitingList.step2') }}</p>
      <p>{{ t('helpCenter.waitingList.step3') }}</p>
      <p>{{ t('helpCenter.waitingList.step4') }}</p>
    </HelpSection>

    <!-- Dummy: Waiting list overview -->
    <NeutralContainer class="space-y-3">
      <SectionHeader>{{ t('waitingList.title') }}</SectionHeader>
      <NeutralContainer class="space-y-2">
        <div class="flex items-center justify-between">
          <span class="font-medium">Warteliste 2026</span>
          <SecondaryBadge>3 Einträge</SecondaryBadge>
        </div>
        <p class="text-sm text-(--text-muted)">Warteliste für neue Mitglieder</p>
      </NeutralContainer>
      <PrimaryButton>{{ t('waitingList.createList') }}</PrimaryButton>
    </NeutralContainer>

    <!-- Dummy: Entry table -->
    <NeutralContainer class="space-y-3">
      <SubHeader>{{ t('waitingList.entries') }}</SubHeader>
      <div class="space-y-2">
        <NeutralContainer class="flex items-center justify-between">
          <div>
            <span class="font-medium">Max Müller</span>
            <MutedText size="sm" class="ml-2">Sabine Müller</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-sm font-mono">9.6</span>
            <SuccessBadge>Aktiv</SuccessBadge>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between">
          <div>
            <span class="font-medium">Sophie Wagner</span>
            <MutedText size="sm" class="ml-2">Klaus Wagner</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-sm font-mono">7.2</span>
            <SecondaryBadge>Aufgenommen</SecondaryBadge>
          </div>
        </NeutralContainer>
      </div>
    </NeutralContainer>

    <!-- Invites section -->
    <HelpSection :title="t('helpCenter.waitingList.invites')">
      <p>{{ t('helpCenter.waitingList.invitesText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-3">
      <SubHeader>{{ t('waitingList.invites') }}</SubHeader>
      <div class="flex items-center justify-between p-2 bg-(--bg-accent)/10 rounded">
        <code class="text-xs">https://ember.local/waiting-list/register?code=abc-123</code>
        <SecondaryButton>Kopieren</SecondaryButton>
      </div>
      <PrimaryButton>{{ t('waitingList.createInvite') }}</PrimaryButton>
    </NeutralContainer>

    <!-- Score formula documentation -->
    <HelpSection :title="t('helpCenter.waitingList.scoring')">
      <p>{{ t('helpCenter.waitingList.scoringText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('helpCenter.waitingList.formulaSyntax') }}</SubHeader>

      <div class="space-y-3 text-sm">
        <div>
          <span class="font-medium">{{ t('helpCenter.waitingList.fieldRef') }}</span>
          <p class="text-(--text-muted)">{{ t('helpCenter.waitingList.fieldRefText') }}</p>
          <CodeSnippet block class="mt-1">[Alter]</CodeSnippet>
        </div>

        <div>
          <span class="font-medium">{{ t('helpCenter.waitingList.operators') }}</span>
          <div class="mt-1 grid grid-cols-2 gap-1 text-xs">
            <CodeSnippet>+ - * /</CodeSnippet>
            <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.arithmetic') }}</span>
            <CodeSnippet>== !=</CodeSnippet>
            <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.equality') }}</span>
            <CodeSnippet>&gt; &lt; &gt;= &lt;=</CodeSnippet>
            <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.comparison') }}</span>
            <CodeSnippet>? :</CodeSnippet>
            <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.ternary') }}</span>
          </div>
        </div>

        <div>
          <span class="font-medium">{{ t('helpCenter.waitingList.examples') }}</span>
          <div class="mt-1 space-y-2">
            <div>
              <CodeSnippet block>[Alter] * 2</CodeSnippet>
              <MutedText tag="p" class="mt-1">{{ t('helpCenter.waitingList.example1') }}</MutedText>
            </div>
            <div>
              <CodeSnippet block>("[Erfahrung]" == "fortgeschritten") * 10 + [Alter]</CodeSnippet>
              <MutedText tag="p" class="mt-1">{{ t('helpCenter.waitingList.example2') }}</MutedText>
            </div>
            <div>
              <CodeSnippet block>[Alter] &gt; 8 ? [Alter] * 2 : [Alter]</CodeSnippet>
              <MutedText tag="p" class="mt-1">{{ t('helpCenter.waitingList.example3') }}</MutedText>
            </div>
          </div>
        </div>
        <div>
          <span class="font-medium">{{ t('helpCenter.waitingList.generatedFields') }}</span>
          <p class="text-(--text-muted)">{{ t('helpCenter.waitingList.generatedFieldsText') }}</p>
          <div class="mt-1 space-y-1 text-xs">
            <div class="flex items-center gap-2">
              <CodeSnippet>[wartezeit_tage]</CodeSnippet>
              <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.waitDays') }}</span>
            </div>
            <div class="flex items-center gap-2">
              <CodeSnippet>[wartezeit_monate]</CodeSnippet>
              <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.waitMonths') }}</span>
            </div>
            <div class="flex items-center gap-2">
              <CodeSnippet>[wartezeit_quartale]</CodeSnippet>
              <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.waitQuarters') }}</span>
            </div>
            <div class="flex items-center gap-2">
              <CodeSnippet>[wartezeit_jahre]</CodeSnippet>
              <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.waitYears') }}</span>
            </div>
            <div class="flex items-center gap-2">
              <CodeSnippet>[alter_Geburtstag]</CodeSnippet>
              <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.ageField') }}</span>
            </div>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <!-- Confirmation flow -->
    <HelpSection :title="t('helpCenter.waitingList.confirmation')">
      <p>{{ t('helpCenter.waitingList.confirmationText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.waitingList.tip') }}</HelpTip>
  </HelpArticle>
</template>
