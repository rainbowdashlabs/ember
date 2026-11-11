/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import ProblemCardHeader from '@/components/problem/ProblemCardHeader.vue'
import ProblemCardDetails from '@/components/problem/ProblemCardDetails.vue'
import ProblemStacktrace from '@/components/problem/ProblemStacktrace.vue'
import type {BeaconFault} from '@/api/generated/schema'
import BeaconFaultContext from './BeaconFaultContext.vue'

/**
 * One fault, read exactly like an entry of this instance's own error log, with the two things only a
 * beacon knows beside it: how many installations met it and which versions they were on.
 */
const props = defineProps<{
  fault: BeaconFault
  expanded: boolean
}>()

const emit = defineEmits<{
  toggle: [id: number]
  resolve: [id: number, version: string | null]
}>()

const {t} = useI18n()

const containerComponent = computed(() => props.fault.level === 'ERROR' ? ErrorContainer : InfoContainer)


/**
 * What the fault is called.
 *
 * <p>The words it was logged with where there are any: a warning without an exception has no class,
 * and one class logs several different failures, so the logger name on its own named nothing an
 * operator could act on. The first line only, since the rest is read by opening the card.
 */
const title = computed(() => {
  const words = props.fault.message?.split('\n')[0]?.trim()
  if (words) return words
  return props.fault.exceptionClass ?? props.fault.logger ?? props.fault.fingerprint
})

function toggle() {
  emit('toggle', props.fault.id)
}
</script>

<template>
  <component
      :is="containerComponent"
      :class="{'opacity-50': fault.acknowledged}"
      class="transition-all cursor-pointer"
      @click="toggle"
  >
    <ProblemCardHeader
        :count="fault.occurrences"
        expandable
        :expanded="expanded"
        :first-occurrence="fault.firstSeen"
        :last-occurrence="fault.lastSeen"
        :level="fault.level"
        :logger="fault.logger"
        :title="title"
    >
      <template #badges>
        <PrimaryBadge :data-testid="`fault-instances-${fault.id}`">
          {{ t('beacon.instancesAffected', {count: fault.instances}) }}
        </PrimaryBadge>
        <SecondaryBadge v-if="fault.resolvedIn">{{ t('beacon.resolvedIn') }} {{ fault.resolvedIn }}</SecondaryBadge>
      </template>
      <template #notes>
        <MutedText v-if="fault.versions.length > 0" tag="p">
          {{ t('beacon.versionsSeen') }}: {{ fault.versions.join(', ') }}
        </MutedText>
      </template>
      <template #actions>
        <IconButton
            v-if="!fault.acknowledged"
            :icon="['fas', 'check']"
            :label="t('beacon.markResolved')"
            @click.stop="emit('resolve', fault.id, fault.versions.at(-1) ?? null)"
        />
      </template>
    </ProblemCardHeader>

    <ProblemCardDetails v-if="expanded" @click.stop>
      <ProblemStacktrace v-if="fault.message" :trace="fault.message" class="mb-3"/>
      <MutedText v-if="fault.exceptionClass" tag="p" size="sm" class="mb-3">{{ fault.exceptionClass }}</MutedText>
      <ProblemStacktrace v-if="fault.frames" :trace="fault.frames"/>
      <BeaconFaultContext :fault-id="fault.id"/>
    </ProblemCardDetails>
  </component>
</template>
