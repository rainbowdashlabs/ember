/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {events} from '@/api'
import type {AwaitingAnswer} from '@/api/events'

/**
 * The events whose registration is running out and which nobody in the household has answered.
 *
 * <p>One row per event, naming who still owes an answer, because a household usually answers the same way
 * for everyone and reading the event once is how somebody decides that.
 *
 * <p>Only events open to the person and still open for answers appear, which is the same question the
 * warning notification asks, so a reader who acts on the notification finds the row gone.
 */
const {t} = useI18n()
const router = useRouter()

const awaiting = ref<AwaitingAnswer[]>([])

/** Whole days until registration closes, floored, so "today" reads as zero rather than as one. */
function daysLeft(entry: AwaitingAnswer): number {
  const closes = new Date(entry.registrationDeadline).getTime()
  return Math.max(0, Math.floor((closes - Date.now()) / 86400000))
}

const soonest = computed(() => awaiting.value.length > 0 ? daysLeft(awaiting.value[0]!) : 0)

async function loadData() {
  try {
    awaiting.value = await events.listAwaitingAnswer()
  } catch { /* a dashboard panel that cannot load says nothing rather than breaking the page */ }
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer v-if="awaiting.length > 0" class="flex flex-col max-h-[66vh]">
    <SectionHeader class="mb-3 shrink-0">
      <font-awesome-icon :icon="['fas', 'clock']" class="mr-2"/>
      {{ t('dashboard.awaitingAnswer') }}
      <InfoBadge v-if="soonest <= 1" class="ml-2">{{ t('dashboard.awaitingSoon') }}</InfoBadge>
    </SectionHeader>
    <div class="overflow-y-auto flex-1 space-y-2">
      <EmptyState compact v-if="awaiting.length === 0">{{ t('dashboard.noAwaitingAnswer') }}</EmptyState>
      <NeutralContainer
          v-for="entry in awaiting"
          :key="entry.eventId"
          data-testid="awaiting-answer"
          class="flex items-center justify-between gap-2 py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
          @click="router.push({name: 'event-detail', params: {id: entry.eventId}})"
      >
        <div>
          <p class="text-sm font-medium">{{ entry.name }}</p>
          <p class="text-xs text-(--text-muted)">
            {{ entry.members.map(m => m.name).join(', ') }}
          </p>
        </div>
        <span class="text-xs text-(--text-muted) shrink-0">
          {{ t('dashboard.awaitingDaysLeft', {days: daysLeft(entry)}) }}
        </span>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
