/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {AckKind, type MovementStep} from '@/api/movements'
import {formatDate} from '@/util/format'

const {t} = useI18n()

const props = defineProps<{
  step: MovementStep
  /** Whether the movement is still walking, which decides if anything ahead is still to come. */
  open: boolean
}>()

/** A step is behind the movement once somebody has acknowledged it. */
const walked = computed(() => !!props.step.ackKind)

/**
 * How the step was acknowledged, in the reader's terms. A step the station vouched for on behalf of
 * a party that cannot answer reads differently from one that party confirmed, and one that was
 * forced past an unresponsive party reads differently again. That difference is the whole reason
 * the chain records it.
 */
const ackLabel = computed(() => (props.step.ackKind ? t(`movements.ack.${props.step.ackKind}`) : ''))
</script>

<template>
  <div class="flex gap-3">
    <div class="flex flex-col items-center pt-1">
      <font-awesome-icon
          :class="walked ? 'text-success' : props.step.current ? 'text-primary' : 'text-(--text-muted)'"
          :icon="['fas', walked ? 'circle-check' : props.step.current ? 'circle-dot' : 'circle']"
      />
      <div class="w-px flex-1 bg-(--border)"/>
    </div>

    <div class="pb-4 flex-1" :class="!walked && !props.step.current ? 'opacity-60' : ''">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-medium text-sm">{{ props.step.label }}</span>
        <SecondaryBadge>{{ t(`movements.actor.${props.step.actor}`) }}</SecondaryBadge>
        <SuccessBadge v-if="props.step.ackKind === AckKind.CONFIRMED">{{ ackLabel }}</SuccessBadge>
        <ErrorBadge v-else-if="props.step.ackKind === AckKind.FORCED">{{ ackLabel }}</ErrorBadge>
        <InfoBadge v-else-if="props.step.ackKind">{{ ackLabel }}</InfoBadge>
      </div>

      <div v-if="walked" class="text-xs text-(--text-muted)">
        {{ props.step.acknowledgedByName }}
        <template v-if="props.step.acknowledgedAt"> · {{ formatDate(props.step.acknowledgedAt) }}</template>
      </div>
      <div v-else-if="props.step.current && props.open" class="text-xs text-(--text-muted)">
        {{ t('movements.waitingFor', {party: t(`movements.actor.${props.step.actor}`)}) }}
      </div>

      <div v-if="props.step.note" class="text-sm mt-1">{{ props.step.note }}</div>

      <slot name="action"/>
    </div>
  </div>
</template>
