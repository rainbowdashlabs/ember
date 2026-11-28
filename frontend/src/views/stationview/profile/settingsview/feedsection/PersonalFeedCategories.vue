/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {EventCategory} from '@/api/generated/schema'
import {events, feedToken} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'
import FeedCategoryChoice from '@/components/input/FeedCategoryChoice.vue'

/**
 * The categories the member's own calendar feed leaves out, saved on every change. A refused save
 * puts the choice back to what is stored, so the toggles never show a filter the feed does not use.
 * Only the answer to the latest change counts, so quick toggles in a row end on what was stored last.
 */
const {t} = useI18n()

const categories = ref<EventCategory[]>([])
const hidden = ref<number[]>([])
const failure = ref<Failure | null>(null)
const ready = ref(false)
const stored = ref<number[]>([])
let latest = 0

onMounted(async () => {
  try {
    const [loadedCategories, loadedHidden] = await Promise.all([events.listCategories(), feedToken.getHiddenCategories()])
    categories.value = loadedCategories
    hidden.value = loadedHidden
    stored.value = loadedHidden
    ready.value = true
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
})

async function change(next: number[]) {
  const attempt = ++latest
  hidden.value = next
  try {
    const answer = await feedToken.setHiddenCategories(next)
    stored.value = answer
    if (attempt !== latest) return
    hidden.value = answer
    failure.value = null
  } catch (e) {
    if (attempt !== latest) return
    hidden.value = stored.value
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <div class="space-y-2">
    <FailureAlert :failure="failure"/>
    <FeedCategoryChoice
        v-if="ready"
        :model-value="hidden"
        :categories="categories"
        :hint="t('calendarFeeds.hiddenCategoriesHint') + ' ' + t('calendarFeeds.personalHint')"
        @update:model-value="change"
    />
  </div>
</template>
