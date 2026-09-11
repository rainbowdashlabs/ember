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
import type {BeaconFault} from '@/api/beacon'

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

const expandable = computed(() => !!props.fault.frames)

const title = computed(() => props.fault.exceptionClass ?? props.fault.logger ?? props.fault.fingerprint)

/** A fault that arrived without its frames does not open, so the card does not invite the click either. */
function toggle() {
  if (expandable.value) emit('toggle', props.fault.id)
}
</script>

<template>
  <component
      :is="containerComponent"
      :class="{'opacity-50': fault.acknowledged, 'cursor-pointer': expandable}"
      class="transition-all"
      @click="toggle"
  >
    <ProblemCardHeader
        :count="fault.occurrences"
        :expandable="expandable"
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

    <ProblemCardDetails v-if="expanded && expandable">
      <ProblemStacktrace :trace="fault.frames ?? ''"/>
    </ProblemCardDetails>
  </component>
</template>
