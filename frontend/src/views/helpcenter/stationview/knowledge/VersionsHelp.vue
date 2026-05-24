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

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.kbVersions.title')" :subtitle="t('helpCenter.kbVersions.subtitle')">
    <HelpSection :title="t('helpCenter.kbVersions.whatIs')">
      <p>{{ t('helpCenter.kbVersions.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbVersions.listTitle')">
      <p>{{ t('helpCenter.kbVersions.listText') }}</p>
    </HelpSection>

    <!-- Dummy: version list -->
    <NeutralContainer class="space-y-2">
      <div class="flex items-center justify-between gap-3 p-2 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">Version 4 <span class="text-xs text-(--text-muted)">(aktuell)</span></div>
          <div class="text-xs text-(--text-muted)">12. Mai 2026 · Max Mustermann</div>
        </div>
        <SecondaryButton class="!text-xs !py-1 !px-2" disabled>
          <font-awesome-icon :icon="['fas', 'eye']" class="mr-1" /> Anzeigen
        </SecondaryButton>
      </div>
      <div class="flex items-center justify-between gap-3 p-2 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">Version 3</div>
          <div class="text-xs text-(--text-muted)">8. Mai 2026 · Anna Beispiel</div>
        </div>
        <div class="flex gap-2">
          <SecondaryButton class="!text-xs !py-1 !px-2" disabled>
            <font-awesome-icon :icon="['fas', 'eye']" class="mr-1" /> Anzeigen
          </SecondaryButton>
          <SecondaryButton class="!text-xs !py-1 !px-2" disabled>
            <font-awesome-icon :icon="['fas', 'code-compare']" class="mr-1" /> Unterschied
          </SecondaryButton>
        </div>
      </div>
      <div class="flex items-center justify-between gap-3 p-2 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">Version 2</div>
          <div class="text-xs text-(--text-muted)">3. Mai 2026 · Max Mustermann</div>
        </div>
        <div class="flex gap-2">
          <SecondaryButton class="!text-xs !py-1 !px-2" disabled>
            <font-awesome-icon :icon="['fas', 'eye']" class="mr-1" /> Anzeigen
          </SecondaryButton>
          <SecondaryButton class="!text-xs !py-1 !px-2" disabled>
            <font-awesome-icon :icon="['fas', 'code-compare']" class="mr-1" /> Unterschied
          </SecondaryButton>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kbVersions.diffTitle')">
      <p>{{ t('helpCenter.kbVersions.diffText') }}</p>
    </HelpSection>

    <!-- Dummy: diff view -->
    <NeutralContainer>
      <div class="font-mono text-xs space-y-0.5">
        <div class="bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300 px-2 py-0.5 rounded-sm">- Erste Hilfe bedeutet sofortige Hilfe zu leisten.</div>
        <div class="bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 px-2 py-0.5 rounded-sm">+ Erste Hilfe bedeutet, einer verletzten Person sofort zu helfen.</div>
        <div class="text-(--text-muted) px-2 py-0.5">  Das kann Leben retten.</div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kbVersions.restoreTitle')">
      <p>{{ t('helpCenter.kbVersions.restoreText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.kbVersions.tip') }}</HelpTip>
  </HelpArticle>
</template>
