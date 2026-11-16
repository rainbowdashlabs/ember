/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useDebounceFn} from '@vueuse/core'
import ViewContent from '@/components/layout/ViewContent.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import {attendance} from '@/api'
import type {SessionSummary} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {usePagedList} from '@/composables/usePagedList'
import PastSessionRow from './pastview/PastSessionRow.vue'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * How many past sheets a page holds. A station that records every evening has hundreds of them, so
 * the list grows with a button rather than arriving whole.
 */
const PAGE_SIZE = 25
/** How long typing rests before the search asks the server again, from the first page. */
const SEARCH_DELAY_MS = 300

const {t} = useI18n()
const {loaded} = useSession()

const search = ref('')
const loading = ref(true)
const failure = ref<Failure | null>(null)

const {items: sessions, hasMore, loadingMore, load, loadMore} = usePagedList<SessionSummary>(
    offset => attendance.listSessionSummaries({search: search.value.trim() || undefined, limit: PAGE_SIZE, offset}),
    PAGE_SIZE,
)

async function guarded(action: () => Promise<void>) {
  failure.value = null
  try {
    await action()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

async function loadFirstPage() {
  await guarded(load)
  loading.value = false
}

const searchLater = useDebounceFn(loadFirstPage, SEARCH_DELAY_MS)

watch(search, () => {
  void searchLater()
})

onMounted(() => {
  if (loaded.value) void loadFirstPage()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) void loadFirstPage()
})
</script>

<template>
  <ViewContent
      :title="t('pages.attendance-past.title')"
      :subtitle="t('pages.attendance-past.subtitle')"
  >
    <div class="space-y-6">
      <SearchInput
          v-model="search"
          :placeholder="t('attendancePast.searchPlaceholder')"
          data-testid="attendance-past-search"
      />
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <template v-if="!loading">
        <div v-if="sessions.length === 0" class="text-(--text-muted) text-sm text-center py-4">
          {{ search.trim() ? t('attendancePast.noMatch') : t('attendancePast.empty') }}
        </div>

        <div class="space-y-2">
          <PastSessionRow v-for="s in sessions" :key="s.id" :session="s"/>
        </div>

        <SecondaryButton
            v-if="hasMore"
            class="w-full"
            :disabled="loadingMore"
            data-testid="attendance-past-more"
            @click="guarded(loadMore)"
        >
          <Spinner v-if="loadingMore" size="sm"/>
          {{ t('attendancePast.loadMore') }}
        </SecondaryButton>
      </template>
    </div>
  </ViewContent>
</template>
