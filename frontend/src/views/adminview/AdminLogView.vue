/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LogEntryRow from '@/components/log/LogEntryRow.vue'
import LogFilterBar from './adminlogview/LogFilterBar.vue'
import {LOG_LEVELS, searchLog, type ApplicationLogPage, type LogEntry} from '@/api/applicationLog'

/**
 * The application log, read from where it is stored rather than from the machine.
 *
 * Searching happens on the server, not in the browser: the point of keeping the log in the database
 * is that a search over weeks of it is an index lookup, and shipping weeks of it to a browser to
 * filter there would undo exactly that.
 */
const {t} = useI18n()

const entries = ref<LogEntry[]>([])
const page = ref<ApplicationLogPage | null>(null)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')

const search = ref('')
const levels = ref<string[]>([...LOG_LEVELS])
const logger = ref('')
const thread = ref('')

const query = computed(() => ({
  levels: levels.value,
  search: search.value,
  logger: logger.value,
  thread: thread.value,
}))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const result = await searchLog(query.value)
    page.value = result
    entries.value = result.entries
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

/** Reads further back from the oldest line on screen, which is what a cursor is for. */
async function loadMore() {
  const oldest = entries.value.at(-1)
  if (!oldest) return
  loadingMore.value = true
  try {
    const result = await searchLog({...query.value, before: oldest.id})
    entries.value = [...entries.value, ...result.entries]
  } catch {
    error.value = t('common.error')
  } finally {
    loadingMore.value = false
  }
}

onMounted(reload)
</script>

<template>
  <ViewContent :title="t('pages.admin-log.title')" :subtitle="t('pages.admin-log.subtitle')">
    <NeutralContainer class="space-y-4">
      <Alert v-if="page && !page.databaseEnabled" variant="info">{{ t('applicationLog.disabled') }}</Alert>
      <Alert v-if="page && page.dropped > 0" variant="error">
        {{ t('applicationLog.dropped', {count: page.dropped}) }}
      </Alert>
      <MutedText v-if="page && page.databaseEnabled" tag="p" size="sm">
        {{ t('applicationLog.kept', {level: page.databaseLevel, days: page.retentionDays}) }}
      </MutedText>

      <LogFilterBar
          v-model:levels="levels"
          v-model:logger="logger"
          v-model:search="search"
          v-model:thread="thread"
          :loading="loading"
          :loggers="page?.loggers ?? []"
          :threads="page?.threads ?? []"
          @change="reload"/>

      <Spinner v-if="loading" size="md"/>
      <Alert v-else-if="error" variant="error">{{ error }}</Alert>
      <template v-else>
        <EmptyHint v-if="entries.length === 0">{{ t('applicationLog.empty') }}</EmptyHint>
        <LogEntryRow v-for="entry in entries" :key="entry.id" :entry="entry"/>
        <div v-if="entries.length > 0" class="flex justify-center">
          <SecondaryButton :icon="['fas', 'arrow-down']" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? t('common.loading') : t('applicationLog.loadMore') }}
          </SecondaryButton>
        </div>
      </template>
    </NeutralContainer>
  </ViewContent>
</template>
