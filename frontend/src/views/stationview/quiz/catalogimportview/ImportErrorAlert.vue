/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import Alert from '@/components/feedback/Alert.vue'
import type {CatalogTransferProblem} from '@/api/quiz'

defineProps<{
  message: string
  problems: CatalogTransferProblem[]
}>()
</script>

<template>
  <Alert v-if="message" variant="error">
    {{ message }}
    <ul v-if="problems.length > 0" class="mt-2 list-disc pl-5 space-y-1">
      <li v-for="problem in problems" :key="problem.location">
        <span class="font-mono">{{ problem.location }}</span>: {{ problem.message }}
      </li>
    </ul>
  </Alert>
</template>
