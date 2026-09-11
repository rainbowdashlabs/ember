/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

/** A stacktrace inside an opened problem, with the button that takes it somewhere it can be read. */
defineProps<{trace: string}>()

const {t} = useI18n()

async function copyStacktrace(text: string) {
  await navigator.clipboard.writeText(text)
}
</script>

<template>
  <div>
    <div class="flex items-center gap-2 mb-1">
      <SubHeader class="!text-xs !mb-0">Stacktrace</SubHeader>
      <IconButton
          :icon="['fas', 'copy']"
          :label="t('adminProblems.copyStacktrace')"
          class="text-(--text-muted) hover:text-primary"
          @click.stop="copyStacktrace(trace)"
      />
    </div>
    <pre class="text-xs font-mono bg-(--bg) rounded p-2 overflow-x-auto max-h-64 whitespace-pre-wrap">{{ trace }}</pre>
  </div>
</template>
