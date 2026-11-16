/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {SetResult} from '@/api/generated/schema'
import {isMet} from '@/util/eventGroupChoice'

/** Marks a sign-up whose parts fall short of a set, naming the sets; nothing where every set is met. */
const props = defineProps<{
  sets: readonly SetResult[]
}>()

const {t} = useI18n()
const short = computed(() => props.sets.filter(result => !isMet(result)))
</script>

<template>
  <ErrorBadge v-if="short.length > 0" :title="t('eventGroups.shortSets', {sets: short.map(result => result.name).join(', ')})"
              data-testid="event-group-short">
    {{ t('eventGroups.short') }}
  </ErrorBadge>
</template>
