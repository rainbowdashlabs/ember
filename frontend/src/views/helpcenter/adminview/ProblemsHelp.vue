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
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminProblems.title')" :subtitle="t('helpCenter.adminProblems.subtitle')">
    <HelpSection :title="t('helpCenter.adminProblems.whatIs')">
      <p>{{ t('helpCenter.adminProblems.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.listTitle')">
      <p>{{ t('helpCenter.adminProblems.listText') }}</p>
    </HelpSection>

    <!-- Dummy: problem log -->
    <NeutralContainer class="space-y-2">
      <div class="flex items-start gap-3 p-3 rounded bg-[var(--bg)] border border-[var(--border)]">
        <ErrorBadge class="mt-0.5 flex-shrink-0">Fehler</ErrorBadge>
        <div class="min-w-0">
          <div class="text-sm font-medium">E-Mail konnte nicht gesendet werden</div>
          <div class="text-xs text-(--text-muted)">12. Mai 2026, 14:32 · 3-mal aufgetreten</div>
          <MutedText tag="div" class="mt-1 font-mono truncate">MailService: Connection refused to smtp.example.com:587</MutedText>
        </div>
      </div>
      <div class="flex items-start gap-3 p-3 rounded bg-[var(--bg)] border border-[var(--border)]">
        <InfoBadge class="mt-0.5 flex-shrink-0">Warnung</InfoBadge>
        <div class="min-w-0">
          <div class="text-sm font-medium">Langsame Datenbankabfrage</div>
          <div class="text-xs text-(--text-muted)">12. Mai 2026, 11:15 · 1-mal aufgetreten</div>
          <MutedText tag="div" class="mt-1 font-mono truncate">KbRepository.search: 2 340 ms (Schwellwert: 500 ms)</MutedText>
        </div>
      </div>
      <div class="flex items-start gap-3 p-3 rounded bg-[var(--bg)] border border-[var(--border)]">
        <ErrorBadge class="mt-0.5 flex-shrink-0">Fehler</ErrorBadge>
        <div class="min-w-0">
          <div class="text-sm font-medium">Unbekannter Fehler beim PDF-Export</div>
          <div class="text-xs text-(--text-muted)">11. Mai 2026, 09:04 · 1-mal aufgetreten</div>
          <MutedText tag="div" class="mt-1 font-mono truncate">TypstCompiler: Process exited with code 1</MutedText>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.adminProblems.groupingTitle')">
      <p>{{ t('helpCenter.adminProblems.groupingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.actionTitle')">
      <p>{{ t('helpCenter.adminProblems.actionText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminProblems.tip') }}</HelpTip>
  </HelpArticle>
</template>
