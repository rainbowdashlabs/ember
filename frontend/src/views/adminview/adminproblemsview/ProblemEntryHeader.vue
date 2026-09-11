/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import ProblemCardHeader from '@/components/problem/ProblemCardHeader.vue'
import {hasDetails, type ProblemEntry} from '@/api/problems'

const props = defineProps<{
  entry: ProblemEntry
  expanded: boolean
  /** Whether this instance reports to a beacon, which is the only case the send button belongs in. */
  canSend?: boolean
}>()

const emit = defineEmits<{
  ack: [id: number]
  send: [id: number]
}>()

const {t} = useI18n()

const expandable = computed(() => hasDetails(props.entry))

const title = computed(() => props.entry.exceptionClass
    ? `${props.entry.exceptionClass}: ${props.entry.exceptionMessage}`
    : (props.entry.distinctMessages[0] ?? ''))
</script>

<template>
  <ProblemCardHeader
      :count="entry.count"
      :expandable="expandable"
      :expanded="expanded"
      :first-occurrence="entry.firstOccurrence"
      :last-occurrence="entry.lastOccurrence"
      :level="entry.level"
      :logger="entry.logger"
      :title="title"
  >
    <template #actions>
      <IconButton
          v-if="canSend"
          :data-testid="`beacon-send-${entry.id}`"
          :icon="['fas', 'tower-broadcast']"
          :label="t('beacon.sendOne')"
          @click.stop="emit('send', entry.id)"
      />
      <IconButton
          v-if="!entry.acknowledged"
          :icon="['fas', 'check']"
          :label="t('adminProblems.acknowledge')"
          @click.stop="emit('ack', entry.id)"
      />
    </template>
  </ProblemCardHeader>
</template>
