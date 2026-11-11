/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import PillBadge from '@/components/badge/PillBadge.vue'
import ProblemCardHeader from '@/components/problem/ProblemCardHeader.vue'
import {hasDetails, problemTitle, type ProblemAction} from '@/api/problems'
import {ProblemLevel, ProblemState, type StoredProblem} from '@/api/generated/schema'

const props = defineProps<{
  entry: StoredProblem
  expanded: boolean
  /** Whether this instance reports to a beacon, which is the only case the send button belongs in. */
  canSend?: boolean
}>()

const emit = defineEmits<{
  act: [action: ProblemAction, id: number]
}>()

const {t} = useI18n()

const expandable = computed(() => hasDetails(props.entry))

const title = computed(() => problemTitle(props.entry))

/** A refusal is never sent to the beacon, so its card never offers to. */
const sendable = computed(() => props.canSend === true && props.entry.level !== ProblemLevel.REFUSAL)

const stateLabel = computed(() => {
  if (props.entry.state === ProblemState.RESOLVED) return t('adminProblems.stateResolved')
  if (props.entry.state === ProblemState.MUTED) return t('adminProblems.stateMuted')
  return null
})
</script>

<template>
  <ProblemCardHeader
      :count="entry.count"
      :expandable="expandable"
      :expanded="expanded"
      :first-occurrence="entry.firstSeen"
      :last-occurrence="entry.lastSeen"
      :level="entry.level"
      :logger="entry.logger"
      :title="title"
  >
    <template #badges>
      <PillBadge v-if="stateLabel" class="text-xs px-1.5 py-0.5 bg-(--bg-accent)" :data-testid="`problem-state-${entry.id}`">
        {{ stateLabel }}
      </PillBadge>
      <PillBadge v-if="entry.reopenedCount > 0" class="text-xs px-1.5 py-0.5 bg-(--bg-accent)">
        {{ t('adminProblems.reopened', {count: entry.reopenedCount}) }}
      </PillBadge>
    </template>
    <template #actions>
      <IconButton
          v-if="sendable"
          :data-testid="`beacon-send-${entry.id}`"
          :icon="['fas', 'tower-broadcast']"
          :label="t('beacon.sendOne')"
          @click.stop="emit('act', 'send', entry.id)"
      />
      <IconButton
          v-if="entry.state === ProblemState.OPEN"
          :data-testid="`problem-resolve-${entry.id}`"
          :icon="['fas', 'check']"
          :label="t('adminProblems.resolve')"
          @click.stop="emit('act', 'resolve', entry.id)"
      />
      <IconButton
          v-if="entry.state !== ProblemState.MUTED"
          :data-testid="`problem-mute-${entry.id}`"
          :icon="['fas', 'bell-slash']"
          :label="t('adminProblems.mute')"
          @click.stop="emit('act', 'mute', entry.id)"
      />
      <IconButton
          v-else
          :data-testid="`problem-unmute-${entry.id}`"
          :icon="['fas', 'bell']"
          :label="t('adminProblems.unmute')"
          @click.stop="emit('act', 'unmute', entry.id)"
      />
      <IconButton
          :data-testid="`problem-delete-${entry.id}`"
          :icon="['fas', 'trash']"
          :label="t('adminProblems.delete')"
          @click.stop="emit('act', 'delete', entry.id)"
      />
    </template>
  </ProblemCardHeader>
</template>
