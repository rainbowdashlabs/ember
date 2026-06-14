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
  // Optional initial value so the badge can paint immediately while the
  // authoritative count is being fetched.
  initialCount?: number
  newsTitle?: string
}>()

const {t} = useI18n()
const modalOpen = ref(false)
const count = ref<number>(props.initialCount ?? 0)

async function fetchCount() {
  try {
    count.value = await newsApi.getNewsViewCount(props.newsId)
  } catch {
    // Best-effort — fall back to whatever we last had.
  }
}

defineExpose({refresh: fetchCount})

onMounted(fetchCount)

function open() {
  modalOpen.value = true
  // Modal load itself fetches the seen list; refresh the count too so the
  // badge can't lag behind a view recorded between mount and click.
  fetchCount()
}
</script>

<template>
  <IconButton
    :icon="['fas', 'eye']"
    :label="t('news.views.openModal')"
    class="text-(--text-muted) hover:text-primary"
    @click.stop="open"
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
