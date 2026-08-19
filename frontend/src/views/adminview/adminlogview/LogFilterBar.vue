/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import LogLevelFilter from './LogLevelFilter.vue'
import LogFacetPanel from './LogFacetPanel.vue'
import {LOG_LEVELS, searchFacets, type LogFacet} from '@/api/applicationLog'

/**
 * Everything the log is narrowed by, in one place.
 *
 * The facet lists come from the page rather than from here, because they are counted over what the
 * filter matches and the server is the only side that knows that.
 */
const props = defineProps<{
  loggers: LogFacet[]
  threads: LogFacet[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'change'): void
}>()

const search = defineModel<string>('search', {required: true})
const levels = defineModel<string[]>('levels', {required: true})
const logger = defineModel<string>('logger', {required: true})
const thread = defineModel<string>('thread', {required: true})

const {t} = useI18n()

const allLevelsChosen = computed(() => levels.value.length === LOG_LEVELS.length)

const query = computed(() => ({
  levels: levels.value,
  search: search.value,
  logger: logger.value,
  thread: thread.value,
}))

function pick(field: 'logger' | 'thread', value: string) {
  if (field === 'logger') logger.value = value
  else thread.value = value
  emit('change')
}

function clearNarrowing() {
  logger.value = ''
  thread.value = ''
  emit('change')
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex gap-2 flex-wrap">
      <TextInput
          v-model="search"
          :aria-label="t('applicationLog.searchPlaceholder')"
          :placeholder="t('applicationLog.searchPlaceholder')"
          class="flex-1 min-w-56"
          @keyup.enter="emit('change')"/>
      <SecondaryButton :disabled="props.loading" :icon="['fas', 'magnifying-glass']" @click="emit('change')">
        {{ t('applicationLog.doSearch') }}
      </SecondaryButton>
    </div>

    <LogLevelFilter v-model="levels" @change="emit('change')"/>
    <MutedText v-if="!allLevelsChosen" size="sm" tag="p">{{ t('applicationLog.filtered') }}</MutedText>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <LogFacetPanel
          :facets="props.loggers"
          :label="t('applicationLog.byLogger')"
          :search="name => searchFacets('logger', name, query)"
          :selected="logger"
          @select="value => pick('logger', value)"/>
      <LogFacetPanel
          :facets="props.threads"
          :label="t('applicationLog.byThread')"
          :search="name => searchFacets('thread', name, query)"
          :selected="thread"
          @select="value => pick('thread', value)"/>
    </div>

    <div v-if="logger || thread" class="flex items-center gap-2 flex-wrap">
      <MutedText size="sm" tag="span">{{ t('applicationLog.narrowedTo') }}</MutedText>
      <span v-if="logger" class="rounded bg-(--bg-accent) px-2 py-0.5 text-xs font-mono">{{ logger }}</span>
      <span v-if="thread" class="rounded bg-(--bg-accent) px-2 py-0.5 text-xs font-mono">{{ thread }}</span>
      <SecondaryButton :icon="['fas', 'xmark']" @click="clearNarrowing">
        {{ t('applicationLog.clearNarrowing') }}
      </SecondaryButton>
    </div>
  </div>
</template>
