/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {CatalogTransferProblem} from '@/api/quiz'
import type {Failure} from '@/util/failure'

/**
 * What stopped the import, in two kinds.
 *
 * <p>The message is what the reader can see for themselves, a file that is not a catalog or a
 * column left unmapped, and it is shown plainly with the rejected rows under it. The failure is
 * what happened on the way to the server, and that one carries the guidance and, where it looks
 * like a fault in Ember, the way to report it.
 */
defineProps<{
  message: string
  failure: Failure | null
  problems: CatalogTransferProblem[]
}>()
</script>

<template>
  <FailureAlert :failure="failure"/>

  <Alert v-if="message" variant="error">
    {{ message }}
    <ul v-if="problems.length > 0" class="mt-2 list-disc pl-5 space-y-1">
      <li v-for="problem in problems" :key="problem.location">
        <span class="font-mono">{{ problem.location }}</span>: {{ problem.message }}
      </li>
    </ul>
  </Alert>
</template>
