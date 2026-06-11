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
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

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

    <HelpSection :title="t('helpCenter.adminProblems.acknowledgeTitle')">
      <p>{{ t('helpCenter.adminProblems.acknowledgeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.groupingTitle')">
      <p>{{ t('helpCenter.adminProblems.groupingText') }}</p>
    </HelpSection>

    <!-- Dummy: Problem log -->
    <HelpSection :title="t('helpCenter.adminProblems.exampleTitle')">
      <!-- Header with actions -->
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-2">
          <SelectionToggleButton :selected="false">
            {{ t('adminProblems.showAcknowledged') }}
          </SelectionToggleButton>
          <SecondaryButton :icon="['fas', 'check-double']">
            {{ t('adminProblems.acknowledgeAll') }}
          </SecondaryButton>
        </div>
      </div>

      <!-- Summary badges -->
      <div class="flex gap-3 mb-4">
        <ErrorContainer class="flex items-center gap-2 !py-2 !px-3">
          <font-awesome-icon :icon="['fas', 'circle-xmark']" />
          <span class="font-semibold">2</span> {{ t('adminProblems.errors') }}
        </ErrorContainer>
        <InfoContainer class="flex items-center gap-2 !py-2 !px-3">
          <font-awesome-icon :icon="['fas', 'triangle-exclamation']" />
          <span class="font-semibold">1</span> {{ t('adminProblems.warnings') }}
        </InfoContainer>
      </div>

      <div class="space-y-2">
        <!-- Error entry (expanded) -->
        <ErrorContainer>
          <div class="flex items-start justify-between gap-3">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs font-mono px-1.5 py-0.5 rounded bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400">ERROR</span>
                <span class="text-xs font-mono text-(--text-muted)">MailService</span>
                <BaseBadge bg-class="bg-[var(--bg-accent)]">3x</BaseBadge>
              </div>
              <p class="text-sm font-medium truncate">MailSendException: Connection refused to smtp.example.com:587</p>
              <p class="text-xs text-(--text-muted)">12.05.2026, 14:32 — 12.05.2026, 16:10</p>
            </div>
            <div class="flex items-center gap-1 shrink-0">
              <IconButton :icon="['fas', 'check']" :label="t('adminProblems.acknowledge')" />
              <font-awesome-icon :icon="['fas', 'chevron-up']" class="text-xs text-(--text-muted)" />
            </div>
          </div>

          <!-- Expanded details -->
          <div class="mt-3 pt-3 border-t border-[var(--border)]">
            <div class="flex items-center gap-2 mb-1">
              <SectionHeader class="!text-xs !mb-0">Stacktrace</SectionHeader>
              <IconButton :icon="['fas', 'copy']" :label="t('adminProblems.copyStacktrace')" class="text-(--text-muted)" />
            </div>
            <pre class="text-xs font-mono bg-[var(--bg)] rounded p-2 overflow-x-auto max-h-32 whitespace-pre-wrap">MailService.send(MailService.java:42)
SmtpTransport.connect(SmtpTransport.java:118)
java.net.ConnectException: Connection refused</pre>
          </div>
        </ErrorContainer>

        <!-- Warning entry (collapsed) -->
        <InfoContainer>
          <div class="flex items-start justify-between gap-3">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs font-mono px-1.5 py-0.5 rounded bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400">WARN</span>
                <span class="text-xs font-mono text-(--text-muted)">KbRepository</span>
              </div>
              <p class="text-sm font-medium truncate">Slow query: KbRepository.search took 2340ms (threshold: 500ms)</p>
              <p class="text-xs text-(--text-muted)">12.05.2026, 11:15</p>
            </div>
            <div class="flex items-center gap-1 shrink-0">
              <IconButton :icon="['fas', 'check']" :label="t('adminProblems.acknowledge')" />
              <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-xs text-(--text-muted)" />
            </div>
          </div>
        </InfoContainer>

        <!-- Error entry (collapsed) -->
        <ErrorContainer>
          <div class="flex items-start justify-between gap-3">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs font-mono px-1.5 py-0.5 rounded bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400">ERROR</span>
                <span class="text-xs font-mono text-(--text-muted)">TypstCompiler</span>
              </div>
              <p class="text-sm font-medium truncate">ProcessException: Process exited with code 1</p>
              <p class="text-xs text-(--text-muted)">11.05.2026, 09:04</p>
            </div>
            <div class="flex items-center gap-1 shrink-0">
              <IconButton :icon="['fas', 'check']" :label="t('adminProblems.acknowledge')" />
              <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-xs text-(--text-muted)" />
            </div>
          </div>
        </ErrorContainer>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminProblems.tip') }}</HelpTip>
  </HelpArticle>
</template>
