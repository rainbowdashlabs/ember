/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ProblemCardDetails from '@/components/problem/ProblemCardDetails.vue'
import ProblemStacktrace from '@/components/problem/ProblemStacktrace.vue'
import type {ProblemEntry} from '@/api/problems'

defineProps<{
  entry: ProblemEntry
}>()

const {t} = useI18n()
</script>

<template>
  <ProblemCardDetails>
    <ProblemStacktrace v-if="entry.stacktrace" :trace="entry.stacktrace" class="mb-3"/>

    <div v-if="entry.distinctMessages.length > 1">
      <SectionHeader class="!text-xs !mb-1">
        {{ t('adminProblems.messages') }} ({{ entry.distinctMessages.length }})
      </SectionHeader>
      <ul class="text-xs space-y-1">
        <li v-for="(msg, idx) in entry.distinctMessages" :key="idx"
            class="font-mono bg-(--bg) rounded px-2 py-1 break-words whitespace-pre-wrap">
          {{ msg }}
        </li>
      </ul>
    </div>
  </ProblemCardDetails>
</template>
