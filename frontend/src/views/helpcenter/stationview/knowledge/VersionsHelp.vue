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
import PrimaryButton from '@/components/button/PrimaryButton.vue'

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
      <div class="flex items-center justify-between gap-2 p-2 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">Version 4 <span class="text-xs text-(--text-muted)">({{ t('helpCenter.kbVersions.current') }})</span></div>
          <div class="text-xs text-(--text-muted)">12. Mai 2026 · Max Mustermann</div>
        </div>
        <div class="flex gap-2">
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'eye']" />
          </SecondaryButton>
          <PrimaryButton compact disabled>
            {{ t('kb.revert') }}
          </PrimaryButton>
        </div>
      </div>
      <div class="flex items-center justify-between gap-2 p-2 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">Version 3</div>
          <div class="text-xs text-(--text-muted)">8. Mai 2026 · Anna Beispiel</div>
        </div>
        <div class="flex gap-2">
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'eye']" />
          </SecondaryButton>
          <PrimaryButton compact disabled>
            {{ t('kb.revert') }}
          </PrimaryButton>
        </div>
      </div>
      <div class="flex items-center justify-between gap-2 p-2 rounded bg-[var(--bg)] border border-[var(--border)]">
        <div>
          <div class="text-sm font-medium">Version 2</div>
          <div class="text-xs text-(--text-muted)">3. Mai 2026 · Max Mustermann</div>
        </div>
        <div class="flex gap-2">
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'eye']" />
          </SecondaryButton>
          <PrimaryButton compact disabled>
            {{ t('kb.revert') }}
          </PrimaryButton>
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
      <p>{{ t('helpCenter.kbVersions.revertConfirmText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.kbVersions.tip') }}</HelpTip>
  </HelpArticle>
</template>
