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
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ArticleComposer from './clusterknowledgeview/ArticleComposer.vue'
import {clusterContent} from '@/api'
import type {ClusterKbFile, ClusterKbFolder} from '@/api/clusterContent'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const folderName = ref('')
const busy = ref(false)

const folders = ref<ClusterKbFolder[]>([])

const {config: files, loading, error, runWith} = useConfigPanel<ClusterKbFile[]>({
  initial: [],
  fetch: async () => {
    folders.value = await clusterContent.listKbFolders()
    return clusterContent.listKbFiles()
  },
})

const editable = hasClusterPermission(ClusterPermission.CLUSTER_KNOWLEDGE_EDIT)

async function addFolder() {
  if (!folderName.value.trim()) return
  await runWith(async () => {
    await clusterContent.createKbFolder(folderName.value.trim())
    folderName.value = ''
    folders.value = await clusterContent.listKbFolders()
    return clusterContent.listKbFiles()
  }, {busy})
}

async function addArticle(name: string, content: string, folderId: number | null) {
  await runWith(async () => {
    await clusterContent.createKbFile({name, content, folderId})
    return clusterContent.listKbFiles()
  }, {busy})
}

async function remove(fileId: number) {
  await runWith(async () => {
    await clusterContent.deleteKbFile(fileId)
    return clusterContent.listKbFiles()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-knowledge.subtitle')" :title="t('pages.cluster-knowledge.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <p class="text-sm text-(--text-muted)">{{ t('clusterKnowledge.hint') }}</p>

      <NeutralContainer v-if="editable" class="space-y-3">
        <SectionHeader>{{ t('clusterKnowledge.folderTitle') }}</SectionHeader>
        <div class="flex items-center gap-2">
          <TextInput v-model="folderName" :placeholder="t('clusterKnowledge.folderPlaceholder')"/>
          <PrimaryButton :disabled="busy || !folderName.trim()" @click="addFolder">
            {{ t('common.create') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>

      <ArticleComposer v-if="editable" :busy="busy" :folders="folders" @publish="addArticle"/>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="files.length === 0">{{ t('clusterKnowledge.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <NeutralContainer v-for="file in files" :key="file.id" class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="font-medium truncate">{{ file.name }}</p>
              <p v-if="file.description" class="text-sm text-(--text-muted) truncate">{{ file.description }}</p>
            </div>
            <DeleteButton v-if="editable" :disabled="busy" @click="remove(file.id)"/>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
