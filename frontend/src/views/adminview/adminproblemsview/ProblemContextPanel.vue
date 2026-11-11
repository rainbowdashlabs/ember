/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {problems} from '@/api'
import {ProblemContextState, type LogEntry, type ProblemContext} from '@/api/generated/schema'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import LogEntryRow from '@/components/log/LogEntryRow.vue'
import PillBadge from '@/components/badge/PillBadge.vue'
import {formatDateTime} from '@/util/format'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The stored log around one occurrence of a problem: the lines of its thread shortly before and after
 * it, or of every thread, with the occurrence's own line marked.
 *
 * <p>It is read when the problem is opened and again whenever another occurrence or the thread switch
 * is chosen; only the answer to the latest choice is shown, and a choice that fails shows no lines
 * rather than the ones of the choice before. An occurrence is chosen by its time, which stays put while
 * the problem keeps happening. Where nothing was kept, it says why rather than showing an empty box.
 */
const props = defineProps<{
  problemId: number
}>()

const {t} = useI18n()

const at = ref<string | null>(null)
const allThreads = ref(false)
const context = ref<ProblemContext | null>(null)
const loading = ref(true)
const failure = ref<Failure | null>(null)
const latestRequest = ref(0)

async function load() {
  const request = latestRequest.value + 1
  latestRequest.value = request
  loading.value = true
  failure.value = null
  try {
    const answer = await problems.context(props.problemId, at.value, allThreads.value)
    if (request === latestRequest.value) context.value = answer
  } catch (e) {
    if (request !== latestRequest.value) return
    context.value = null
    failure.value = describeFailure(e, t)
  } finally {
    if (request === latestRequest.value) loading.value = false
  }
}

watch([at, allThreads], load, {immediate: true})

/** When the occurrence the lines are around happened, as the last answer says. */
const shownAt = computed(() => context.value?.occurrences[context.value.occurrence]?.occurredAt ?? null)

/** Lines were found, but the problem's own line is not among them: it was never written to the log. */
const ownLineMissing = computed(() =>
  context.value?.state === ProblemContextState.LINES && !context.value.lines.some(line => line.problem))

/** The lines in the shape the log's own row draws, which carries an id and a trace these do not. */
const rows = computed<{entry: LogEntry, problem: boolean}[]>(() => (context.value?.lines ?? []).map((line, index) => ({
  entry: {id: index, loggedAt: line.loggedAt, level: line.level, logger: line.logger, thread: line.thread,
    message: line.message, throwable: null},
  problem: line.problem,
})))

const emptyText = computed(() => {
  switch (context.value?.state) {
    case ProblemContextState.LOGGING_OFF: return t('adminProblems.contextLoggingOff')
    case ProblemContextState.NOTHING_KEPT: return t('adminProblems.contextNothingKept')
    case ProblemContextState.NO_OCCURRENCE: return t('adminProblems.contextNoOccurrence')
    default: return ''
  }
})
</script>

<template>
  <div class="mt-3" data-testid="problem-context">
    <SectionHeader class="!text-xs !mb-1">{{ t('adminProblems.contextTitle') }}</SectionHeader>
    <div class="flex flex-wrap items-center gap-2 mb-2">
      <template v-if="(context?.occurrences.length ?? 0) > 1">
        <SelectionToggleButton
            v-for="(kept, index) in context?.occurrences ?? []"
            :key="index"
            size="sm"
            :selected="shownAt === kept.occurredAt"
            :data-testid="`problem-occurrence-${index}`"
            @toggle="at = kept.occurredAt"
        >
          {{ formatDateTime(kept.occurredAt) }}
        </SelectionToggleButton>
      </template>
      <SelectionToggleButton size="sm" :selected="allThreads" data-testid="problem-all-threads" @toggle="allThreads = !allThreads">
        {{ t('adminProblems.contextAllThreads') }}
      </SelectionToggleButton>
    </div>

    <FailureAlert :failure="failure" class="mb-2"/>
    <div v-if="loading" role="status" :aria-label="t('adminProblems.contextLoading')">
      <Spinner size="sm"/>
    </div>
    <p v-else-if="emptyText" class="text-xs text-(--text-muted)" data-testid="problem-context-empty">
      {{ emptyText }}
      <NuxtLink v-if="context?.state === ProblemContextState.LOGGING_OFF" to="/admin/settings" class="underline">
        {{ t('adminProblems.contextSettings') }}
      </NuxtLink>
    </p>
    <template v-else-if="context">
      <p v-if="ownLineMissing" class="text-xs text-(--text-muted) mb-1" data-testid="problem-context-own-missing">
        {{ t('adminProblems.contextOwnLineMissing') }}
      </p>
      <ul class="space-y-1">
        <li
            v-for="row in rows"
            :key="row.entry.id"
            :class="{'ring-2 ring-primary rounded-lg': row.problem}"
            :aria-current="row.problem ? 'true' : undefined"
            :data-testid="row.problem ? 'problem-context-occurrence' : undefined"
        >
          <PillBadge v-if="row.problem" class="text-xs px-1.5 py-0.5 bg-(--bg-accent) m-1">
            {{ t('adminProblems.contextThisEntry') }}
          </PillBadge>
          <LogEntryRow :entry="row.entry"/>
        </li>
      </ul>
    </template>
  </div>
</template>
