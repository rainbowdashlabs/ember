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
import type {ProblemEntry} from '@/api/problems'

const props = defineProps<{
  entry: ProblemEntry
  expanded: boolean
}>()

const emit = defineEmits<{
  toggle: [id: number]
  ack: [id: number]
}>()

const containerComponent = computed(() => props.entry.level === 'ERROR' ? ErrorContainer : InfoContainer)
</script>

<template>
  <component
    :is="containerComponent"
    class="cursor-pointer transition-all"
    :class="{'opacity-50': entry.acknowledged}"
    @click="emit('toggle', entry.id)"
  >
    <ProblemEntryHeader :entry="entry" :expanded="expanded" @ack="emit('ack', $event)"/>
    <ProblemEntryDetails v-if="expanded" :entry="entry"/>
  </component>
</template>
