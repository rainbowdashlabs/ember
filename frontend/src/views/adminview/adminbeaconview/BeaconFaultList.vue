/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import ProblemLevelSummary from '@/components/problem/ProblemLevelSummary.vue'
import BeaconFaultCard from './BeaconFaultCard.vue'
import type {BeaconFault} from '@/api/beacon'

/**
 * The error log of the installations that report here, filtered and counted the way this instance's
 * own log is.
 */
const props = defineProps<{
  faults: BeaconFault[]
  showAcknowledged: boolean
}>()

const emit = defineEmits<{
  toggleAcknowledged: []
  resolve: [id: number, version: string | null]
}>()

const {t} = useI18n()

const levelFilter = ref<string | null>(null)
const expandedId = ref<number | null>(null)

const errorCount = computed(() => props.faults.filter(f => f.level === 'ERROR' && !f.acknowledged).length)
const warnCount = computed(() => props.faults.filter(f => f.level === 'WARN' && !f.acknowledged).length)

const visibleFaults = computed(() =>
    levelFilter.value == null ? props.faults : props.faults.filter(f => f.level === levelFilter.value))

function toggleLevelFilter(level: string) {
  levelFilter.value = levelFilter.value === level ? null : level
}

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

function resolve(id: number, version: string | null) {
  emit('resolve', id, version)
}
</script>

<template>
  <div>
    <div class="flex items-center gap-2 mb-4">
      <SelectionToggleButton :selected="showAcknowledged" @toggle="emit('toggleAcknowledged')">
        {{ t('adminProblems.showAcknowledged') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="levelFilter === 'ERROR'" @toggle="toggleLevelFilter('ERROR')">
        {{ t('adminProblems.errors') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="levelFilter === 'WARN'" @toggle="toggleLevelFilter('WARN')">
        {{ t('adminProblems.warnings') }}
      </SelectionToggleButton>
    </div>

    <ProblemLevelSummary :empty-label="t('beacon.noFaults')" :error-count="errorCount" :warn-count="warnCount"/>

    <div class="space-y-2">
      <BeaconFaultCard
          v-for="fault in visibleFaults"
          :key="fault.id"
          :expanded="expandedId === fault.id"
          :fault="fault"
          @resolve="resolve"
          @toggle="toggleExpand"
      />
    </div>
  </div>
</template>
