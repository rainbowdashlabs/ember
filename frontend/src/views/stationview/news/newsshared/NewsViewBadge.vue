/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import NewsViewsModal from '@/views/stationview/news/listview/NewsViewsModal.vue'
import {news as newsApi} from '@/api'

const props = defineProps<{
  newsId: number
  /** Painted at once while the authoritative count is being fetched. */
  initialCount?: number
  newsTitle?: string
}>()

const {t} = useI18n()
const modalOpen = ref(false)
const count = ref<number>(props.initialCount ?? 0)

/** Reads the current count, keeping the last one where the read fails. */
async function fetchCount() {
  await newsApi.getNewsViewCount(props.newsId).then(viewed => { count.value = viewed }).catch(() => {})
}

defineExpose({refresh: fetchCount})

onMounted(fetchCount)

/** Opens the list of readers and refreshes the count too, so it cannot lag behind a view recorded since mount. */
function open() {
  modalOpen.value = true
  fetchCount()
}
</script>

<template>
  <IconButton
    :icon="['fas', 'eye']"
    :label="t('news.views.openModal')"
    class="text-(--text-muted) hover:text-primary"
    @click="open"
  >
    <span class="inline-flex items-center gap-1.5">
      <font-awesome-icon :icon="['fas', 'eye']" class="h-4 w-4"/>
      <span class="text-xs tabular-nums">{{ count }}</span>
    </span>
  </IconButton>

  <NewsViewsModal
    v-model="modalOpen"
    :news-id="props.newsId"
    :news-title="props.newsTitle"
  />
</template>
