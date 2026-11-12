/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {beacon} from '@/api'
import type {BeaconProblemContext, LogEntry} from '@/api/generated/schema'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import LogEntryRow from '@/components/log/LogEntryRow.vue'
import {formatDateTime} from '@/util/format'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The log lines instances sent with one fault, per instance and occurrence, the newest first. They
 * arrive redacted by the instance that sent them; nothing here changes them.
 */
const props = defineProps<{
  faultId: number
}>()

const {t} = useI18n()

const contexts = ref<BeaconProblemContext[]>([])
const loading = ref(true)
const failure = ref<Failure | null>(null)

onMounted(async () => {
  try {
    contexts.value = await beacon.faultContext(props.faultId)
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    loading.value = false
  }
})

/** Each delivery's lines in the shape the log's own row draws, which carries an id and a trace these do not. */
const deliveries = computed(() => contexts.value.map((context, index) => ({
  key: `${context.instanceId}-${context.receivedAt}-${index}`,
  heading: t('beacon.contextFrom', {
    instance: context.contactName ?? t('beacon.contextUnnamed'),
    version: context.version ?? '?',
    occurred: formatDateTime(context.occurredAt),
  }),
  rows: context.lines.map((line, row): LogEntry => ({
    id: row, loggedAt: line.loggedAt, level: line.level, logger: line.logger, thread: line.thread,
    message: line.message, throwable: null,
  })),
})))
</script>

<template>
  <div class="mt-3" data-testid="beacon-fault-context">
    <SectionHeader class="!text-xs !mb-1">{{ t('beacon.contextTitle') }}</SectionHeader>
    <FailureAlert :failure="failure" class="mb-2"/>
    <div v-if="loading" role="status" :aria-label="t('beacon.contextLoading')">
      <Spinner size="sm"/>
    </div>
    <p v-else-if="deliveries.length === 0" class="text-xs text-(--text-muted)">{{ t('beacon.contextNone') }}</p>
    <template v-else>
      <div v-for="delivery in deliveries" :key="delivery.key" class="mb-3">
        <p class="text-xs text-(--text-muted) mb-1">{{ delivery.heading }}</p>
        <ul class="space-y-1">
          <li v-for="row in delivery.rows" :key="row.id">
            <LogEntryRow :entry="row"/>
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>
