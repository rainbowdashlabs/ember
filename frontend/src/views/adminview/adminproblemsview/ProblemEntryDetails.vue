/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {ProblemEntry} from '@/api/problems'

defineProps<{
  entry: ProblemEntry
}>()

const {t} = useI18n()

async function copyStacktrace(text: string) {
  await navigator.clipboard.writeText(text)
}
</script>

<template>
  <div class="mt-3 pt-3 border-t border-[var(--border)]">
    <div v-if="entry.stacktrace" class="mb-3">
      <div class="flex items-center gap-2 mb-1">
        <SubHeader class="!text-xs !mb-0">Stacktrace</SubHeader>
        <IconButton
          :icon="['fas', 'copy']"
          :label="t('adminProblems.copyStacktrace')"
          class="text-[var(--text-muted)] hover:text-primary"
          @click.stop="copyStacktrace(entry.stacktrace)"
        />
      </div>
      <pre class="text-xs font-mono bg-[var(--bg)] rounded p-2 overflow-x-auto max-h-64 whitespace-pre-wrap">{{ entry.stacktrace }}</pre>
    </div>

    <div v-if="entry.distinctMessages.length > 1">
      <SectionHeader class="!text-xs !mb-1">
        {{ t('adminProblems.messages') }} ({{ entry.distinctMessages.length }})
      </SectionHeader>
      <ul class="text-xs space-y-1">
        <li v-for="(msg, idx) in entry.distinctMessages" :key="idx"
            class="font-mono bg-[var(--bg)] rounded px-2 py-1 truncate" :title="msg">
          {{ msg }}
        </li>
      </ul>
    </div>
  </div>
</template>
