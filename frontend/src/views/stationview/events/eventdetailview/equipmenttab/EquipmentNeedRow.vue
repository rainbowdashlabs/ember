/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {NeedCoverage} from '@/api/equipment'
import {formatDateTime} from '@/util/format'

const props = defineProps<{
  coverage: NeedCoverage
  editable: boolean
}>()

const emit = defineEmits<{
  remove: [needId: number]
}>()

const {t} = useI18n()

/**
 * Where the pieces come from, said as one sentence.
 *
 * The line is the question and the origin is part of the answer: counting only the station's own
 * stock would report a shortfall that was solved a week ago.
 */
const split = computed(() =>
    t('eventEquipment.split', {
      own: props.coverage.own,
      borrowed: props.coverage.borrowed,
      outstanding: props.coverage.outstanding,
    }))

const overClaimNames = computed(() =>
    props.coverage.overClaim
        .map(claim => claim.label)
        .filter(label => !!label)
        .join(', '))
</script>

<template>
  <div
      class="flex flex-col gap-1 px-3 py-3 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 last:border-b-0"
      data-testid="equipment-need"
  >
    <div class="flex flex-wrap items-center gap-2">
      <span class="text-sm font-medium">{{ coverage.need.quantity }}x {{ coverage.label }}</span>
      <SuccessBadge v-if="coverage.covered" data-testid="equipment-need-covered">
        {{ t('eventEquipment.covered') }}
      </SuccessBadge>
      <ErrorBadge v-else data-testid="equipment-need-missing">
        {{ t('eventEquipment.missing', {count: coverage.missing}) }}
      </ErrorBadge>
      <InfoBadge v-if="coverage.need.eventDate" data-testid="equipment-need-once">
        {{ t('eventEquipment.thisEveningOnly') }}
      </InfoBadge>
      <DeleteButton v-if="editable" data-testid="equipment-need-remove" @click="emit('remove', coverage.need.id)"/>
    </div>

    <span class="text-sm text-(--text-muted)" data-testid="equipment-need-split">{{ split }}</span>

    <span class="text-xs text-(--text-muted)">
      {{ t('eventEquipment.away', {from: formatDateTime(coverage.from), to: formatDateTime(coverage.to)}) }}
    </span>

    <span v-if="overClaimNames" class="text-sm" data-testid="equipment-need-overclaim">
      {{ t('eventEquipment.overClaim', {names: overClaimNames}) }}
    </span>
  </div>
</template>
