/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {JobMemberResult} from '@/api/generated/schema'
import {failuresOf, failureText} from '@/components/documents/bulk/bulkGeneration'

/** The members of a run whose document could not be filed, each with the reason. */
const props = defineProps<{
  members: JobMemberResult[]
}>()

const {t} = useI18n()

const failures = computed(() => failuresOf(props.members))
</script>

<template>
  <ul class="list-disc pl-5 text-sm space-y-1" data-testid="generation-job-failures">
    <li v-for="member in failures" :key="member.memberId">
      <span class="font-semibold">{{ member.name }}</span>: {{ failureText(member, t) }}
    </li>
  </ul>
</template>
