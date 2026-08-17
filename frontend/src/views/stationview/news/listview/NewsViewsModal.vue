/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {news as newsApi} from '@/api'
import type {NewsViewsResponse} from '@/api/news'
import {formatDateTime} from '@/util/format'

const open = defineModel<boolean>({required: true})

const props = defineProps<{
  newsId: number | null
  newsTitle?: string
}>()

const {t} = useI18n()

const data = ref<NewsViewsResponse | null>(null)
const loading = ref(false)
const error = ref('')

watch(open, async (v) => {
  if (v && props.newsId != null) {
    await load(props.newsId)
  }
})

async function load(newsId: number) {
  loading.value = true
  error.value = ''
  data.value = null
  try {
    data.value = await newsApi.listNewsViewers(newsId)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4 max-w-xl">
      <SubHeader>
        {{ t('news.views.title') }}
        <MutedText v-if="newsTitle" tag="span" size="sm" class="ml-1">- {{ newsTitle }}</MutedText>
      </SubHeader>

      <Spinner v-if="loading" size="md"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && data">
        <section class="space-y-2">
          <div class="flex items-baseline gap-2">
            <p class="font-semibold text-sm">{{ t('news.views.seen') }}</p>
            <MutedText size="sm">({{ data.seen.length }})</MutedText>
          </div>
          <p v-if="data.seen.length === 0" class="text-sm text-(--text-muted) italic">
            {{ t('news.views.noneSeen') }}
          </p>
          <ul v-else class="space-y-1.5 max-h-64 overflow-y-auto pr-1">
            <li v-for="entry in data.seen" :key="entry.member.memberUid"
                class="flex items-center justify-between gap-3 text-sm">
              <MemberName :identity="entry.member"/>
              <MutedText size="xs">{{ formatDateTime(entry.seenAt) }}</MutedText>
            </li>
          </ul>
        </section>

        <section class="space-y-2 pt-2 border-t border-(--border)">
          <div class="flex items-baseline gap-2">
            <p class="font-semibold text-sm">{{ t('news.views.unseen') }}</p>
            <MutedText size="sm">({{ data.unseen.length }})</MutedText>
          </div>
          <p v-if="data.unseen.length === 0" class="text-sm text-(--text-muted) italic">
            {{ t('news.views.allSeen') }}
          </p>
          <ul v-else class="space-y-1.5 max-h-64 overflow-y-auto pr-1">
            <li v-for="entry in data.unseen" :key="entry.member.memberUid"
                class="flex items-center text-sm">
              <MemberName :identity="entry.member"/>
            </li>
          </ul>
        </section>
      </template>
    </div>
  </Modal>
</template>
