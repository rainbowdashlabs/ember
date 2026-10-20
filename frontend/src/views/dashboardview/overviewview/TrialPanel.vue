/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {waitingList} from '@/api'
import type {TrialOverview} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDate} from '@/util/format'

/**
 * The trials the reader's household is in: since when, how often somebody was there, and how many
 * visits the station looks for before it decides.
 *
 * <p>Reaching that number changes nothing by itself, so the panel only shows it. Joining stays the
 * station's decision. What the family can decide is to stop, which ends the trial behind a
 * confirmation.
 */
const {t} = useI18n()

const trials = ref<TrialOverview[]>([])
const ending = ref<TrialOverview | null>(null)

/** Loads the panel, which says nothing when it cannot load rather than breaking the page. */
async function load() {
  trials.value = await waitingList.listOwnTrials().catch(() => [])
}

const {running, failure, run: endTrial} = useAsyncAction(async () => {
  if (!ending.value) return
  await waitingList.endOwnTrial(ending.value.entryId)
  ending.value = null
  await load()
})

function onConfirmOpen(open: boolean) {
  if (!open) ending.value = null
}

onMounted(load)
</script>

<template>
  <NeutralContainer v-if="trials.length > 0" class="space-y-3" data-testid="trial-panel">
    <SectionHeader>
      <font-awesome-icon :icon="['fas', 'hourglass-half']" class="mr-2"/>
      {{ t('dashboard.trial.title') }}
    </SectionHeader>
    <NeutralContainer
        v-for="trial in trials"
        :key="trial.entryId"
        class="flex flex-wrap items-center justify-between gap-2 py-2 px-3"
        data-testid="trial-row"
    >
      <div class="min-w-0">
        <p class="truncate text-sm font-medium">{{ trial.name }}</p>
        <p class="text-xs text-(--text-muted)">
          {{ t('dashboard.trial.since', {list: trial.listName, date: formatDate(trial.since)}) }}
        </p>
        <p class="text-xs" data-testid="trial-visits">
          <template v-if="trial.attendanceThreshold > 0">
            {{ t('dashboard.trial.visitsOf', {visits: trial.visits, threshold: trial.attendanceThreshold}) }}
          </template>
          <template v-else>{{ t('dashboard.trial.visits', {visits: trial.visits}) }}</template>
        </p>
      </div>
      <ErrorButton :icon="['fas', 'user-minus']" data-testid="trial-end" @click="ending = trial">
        {{ t('dashboard.trial.end') }}
      </ErrorButton>
    </NeutralContainer>
    <FailureAlert :failure="failure"/>
    <ConfirmDeleteModal
        :model-value="ending !== null"
        :title="t('dashboard.trial.endTitle')"
        :message="t('dashboard.trial.endText', {name: ending?.name ?? ''})"
        :confirm-label="t('dashboard.trial.end')"
        :busy="running"
        confirm-test-id="trial-end-confirm"
        @update:model-value="onConfirmOpen"
        @confirm="endTrial"
    />
  </NeutralContainer>
</template>
