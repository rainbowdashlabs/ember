/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import DragList from '@/components/input/DragList.vue'
import type {QueueEntry} from '@/util/registrationQueue'

/**
 * The waiting list of a date, in the order it moves up in.
 *
 * <p>Whoever decides the sign-ups puts it in another order with the arrows or by dragging, and moves
 * somebody up by hand ahead of it. Members of the station and partners' members wait in the one list.
 * A partner who decides its own people keeps them: their rows show where they stand and offer
 * nothing.
 */
const props = defineProps<{
  entries: QueueEntry[]
  /** Whether the reader decides the sign-ups, which is what the order and moving up are for. */
  canManage: boolean
  /** Whether a partner decides about its own people, whose rows then offer nothing. */
  partnerDecides?: (partnerId: number) => boolean
  busy?: boolean
}>()

const emit = defineEmits<{
  reorder: [entry: QueueEntry, position: number]
  moveUp: [entry: QueueEntry]
}>()

const {t} = useI18n()

/** Whether a row may be acted on here. */
function actionable(entry: QueueEntry): boolean {
  return props.canManage && !(entry.partnerId !== null && props.partnerDecides?.(entry.partnerId))
}

/** Moving a row puts it at the place it was moved to, counted from 1. */
function onReorder(fromIndex: number, toIndex: number) {
  const entry = props.entries[fromIndex]
  if (entry) emit('reorder', entry, toIndex + 1)
}
</script>

<template>
  <section v-if="entries.length > 0" class="space-y-2" data-testid="queue-section">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'list-ol']" class="mr-1"/>
      {{ t('registrationQueue.title') }} ({{ entries.length }})
    </SubHeader>
    <MutedText size="sm" tag="p">{{ t('registrationQueue.sectionHint') }}</MutedText>
    <DragList
        :items="entries"
        :key-fn="entry => `${entry.kind}-${entry.id}`"
        :disabled="!canManage || busy"
        @reorder="onReorder"
    >
      <template #default="{item}">
        <div class="flex flex-wrap items-center justify-between gap-2 py-1" :data-testid="`queue-entry-${item.kind}-${item.id}`">
          <div class="flex items-center gap-2 min-w-0">
            <span class="w-6 text-right font-semibold tabular-nums">{{ item.position }}.</span>
            <MemberName v-if="item.identity" :identity="item.identity"/>
            <span v-else>{{ item.name }}</span>
            <SecondaryBadge v-if="item.kind === 'partner'">{{ t('registrationQueue.partner') }}</SecondaryBadge>
          </div>
          <PrimaryButton
              v-if="actionable(item)"
              compact
              :icon="['fas', 'circle-up']"
              :disabled="busy"
              :data-testid="`queue-move-up-${item.kind}-${item.id}`"
              @click="emit('moveUp', item)"
          >
            {{ t('registrationQueue.moveUp') }}
          </PrimaryButton>
        </div>
      </template>
    </DragList>
  </section>
</template>
