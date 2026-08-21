/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {clusterContent} from '@/api'
import type {ClusterNews} from '@/api/clusterContent'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'
import {formatDate} from '@/util/format'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const title = ref('')
const body = ref('')
const busy = ref(false)

const {config: news, loading, error, runWith} = useConfigPanel<ClusterNews[]>({
  initial: [],
  fetch: () => clusterContent.listNews(),
})

const editable = hasClusterPermission(ClusterPermission.CLUSTER_NEWS_EDIT)

async function publish() {
  if (!title.value.trim()) return
  await runWith(async () => {
    // The markdown is the source; the rendered copy is what readers see
    await clusterContent.createNews(title.value.trim(), body.value, body.value)
    title.value = ''
    body.value = ''
    return clusterContent.listNews()
  }, {busy})
}

async function remove(newsId: number) {
  await runWith(async () => {
    await clusterContent.deleteNews(newsId)
    return clusterContent.listNews()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-news.subtitle')" :title="t('pages.cluster-news.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <p class="text-sm text-(--text-muted)">{{ t('clusterNews.hint') }}</p>

      <NeutralContainer v-if="editable" class="space-y-4">
        <SectionHeader>{{ t('clusterNews.writeTitle') }}</SectionHeader>
        <div class="space-y-1">
          <FormLabel>{{ t('clusterNews.titleLabel') }}</FormLabel>
          <TextInput v-model="title" :placeholder="t('clusterNews.titlePlaceholder')"/>
        </div>
        <div class="space-y-1">
          <FormLabel>{{ t('clusterNews.bodyLabel') }}</FormLabel>
          <TextAreaInput v-model="body" :rows="6"/>
        </div>
        <PrimaryButton :disabled="busy || !title.trim()" @click="publish">
          {{ t('clusterNews.publish') }}
        </PrimaryButton>
      </NeutralContainer>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="news.length === 0">{{ t('clusterNews.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <NeutralContainer v-for="entry in news" :key="entry.id" class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="font-medium">{{ entry.title }}</p>
              <p class="text-sm text-(--text-muted)">{{ formatDate(entry.createdAt) }}</p>
            </div>
            <DeleteButton v-if="editable" :disabled="busy" @click="remove(entry.id)"/>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
