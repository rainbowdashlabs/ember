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
import {hasDetails, type ProblemAction} from '@/api/problems'
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

const expandable = computed(() => hasDetails(props.entry))

/** An entry with nothing behind it does not open, so the card does not invite the click either. */
function toggle() {
  if (expandable.value) emit('toggle', props.entry.id)
}
</script>

<template>
  <component
    :is="containerComponent"
    class="transition-all"
    :class="{'opacity-50': entry.state !== ProblemState.OPEN, 'cursor-pointer': expandable}"
    @click="toggle"
  >
    <ProblemEntryHeader
      :entry="entry"
      :expanded="expanded"
      :can-send="canSend"
      @act="(action, id) => emit('act', action, id)"
    />
    <ProblemEntryDetails v-if="expanded && expandable" :entry="entry"/>
  </component>
</template>
