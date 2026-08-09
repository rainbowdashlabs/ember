/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <details class="text-sm">
    <summary class="cursor-pointer text-primary font-medium">{{ t('memberImport.helpTitle') }}</summary>
    <div class="mt-2 space-y-3 text-(--text-muted) bg-bg-light-accent/10 dark:bg-bg-dark-accent/10 rounded-lg p-4">
      <div>
        <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpMergeTitle') }}</p>
        <p>{{ t('memberImport.helpMerge') }}</p>
      </div>
      <div>
        <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpSplitTitle') }}</p>
        <p>{{ t('memberImport.helpSplit') }}</p>
      </div>
      <div>
        <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpValueMapTitle') }}</p>
        <p>{{ t('memberImport.helpValueMap') }}</p>
      </div>
    </div>
  </details>
</template>
