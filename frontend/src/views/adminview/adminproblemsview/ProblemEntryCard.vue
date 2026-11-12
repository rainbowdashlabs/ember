/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ProblemEntryHeader from './ProblemEntryHeader.vue'
import ProblemEntryDetails from './ProblemEntryDetails.vue'
import type {ProblemAction} from '@/api/problems'
import {ProblemLevel, ProblemState, type StoredProblem} from '@/api/generated/schema'

const props = defineProps<{
  entry: StoredProblem
  expanded: boolean
  /** Whether this instance reports to a beacon, which is the only case the send button belongs in. */
  canSend?: boolean
}>()

const emit = defineEmits<{
  toggle: [id: number]
  act: [action: ProblemAction, id: number]
}>()

const containerComponent = computed(() => props.entry.level === ProblemLevel.ERROR ? ErrorContainer : InfoContainer)

/** Every entry opens: there is always at least the log around it to look at. */
function toggle() {
  emit('toggle', props.entry.id)
}
</script>

<template>
  <component
    :is="containerComponent"
    class="transition-all cursor-pointer"
    :class="{'opacity-50': entry.state !== ProblemState.OPEN}"
    @click="toggle"
  >
    <ProblemEntryHeader
      :entry="entry"
      :expanded="expanded"
      :can-send="canSend"
      @act="(action, id) => emit('act', action, id)"
    />
    <ProblemEntryDetails v-if="expanded" :entry="entry" @click.stop/>
  </component>
</template>
