/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { marked } from 'marked'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type { MemberGroup } from '@/api/types'
import { news, memberGroups } from '@/api'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const newsId = computed(() => isEdit.value ? Number(route.params.id) : null)

const title = ref('')
const contentMarkdown = ref('')
const selectedGroupIds = ref<Set<number>>(new Set())
const groups = ref<MemberGroup[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const showPreview = ref(false)

const contentHtml = computed(() => {
  try {
    return marked.parse(contentMarkdown.value) as string
  } catch {
    return ''
  }
})

async function loadData() {
  loading.value = true
  try {
    groups.value = await memberGroups.listGroups()
    if (newsId.value) {
      const entry = await news.getNews(newsId.value)
      title.value = entry.title
      contentMarkdown.value = entry.contentMarkdown
      selectedGroupIds.value = new Set(entry.groupIds)
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function toggleGroup(groupId: number) {
  const s = new Set(selectedGroupIds.value)
  if (s.has(groupId)) s.delete(groupId); else s.add(groupId)
  selectedGroupIds.value = s
}

async function save() {
  if (!title.value.trim() || !contentMarkdown.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    const data = {
      title: title.value,
      contentMarkdown: contentMarkdown.value,
      contentHtml: contentHtml.value,
      groupIds: [...selectedGroupIds.value],
    }
    if (newsId.value) {
      await news.updateNews(newsId.value, data)
    } else {
      await news.createNews(data)
    }
    await router.push({ name: 'news-list' })
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ isEdit ? t('news.editTitle') : t('news.createTitle') }}</SectionHeader>
        <SecondaryButton @click="router.push({ name: 'news-list' })">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('news.titleField') }}</label>
            <TextInput v-model="title" :placeholder="t('news.titlePlaceholder')" />
          </div>

          <div class="space-y-1">
            <div class="flex items-center justify-between">
              <label class="block text-sm font-medium">{{ t('news.content') }}</label>
              <button
                type="button"
                class="text-xs text-primary hover:underline"
                @click="showPreview = !showPreview"
              >
                {{ showPreview ? t('news.hidePreview') : t('news.showPreview') }}
              </button>
            </div>
            <TextAreaInput v-model="contentMarkdown" :placeholder="t('news.contentPlaceholder')" :rows="12" />
            <p class="text-xs text-(--text-muted)">{{ t('news.markdownHint') }}</p>
          </div>

          <div v-if="showPreview && contentHtml" class="space-y-1">
            <SubHeader>{{ t('news.preview') }}</SubHeader>
            <NeutralContainer class="prose prose-sm dark:prose-invert max-w-none" v-html="contentHtml" />
          </div>
        </NeutralContainer>

        <NeutralContainer v-if="groups.length > 0" class="space-y-3">
          <SubHeader>{{ t('news.restrictToGroups') }}</SubHeader>
          <p class="text-xs text-(--text-muted)">{{ t('news.restrictHint') }}</p>
          <div class="flex flex-wrap gap-2">
            <SelectionToggleButton
              v-for="group in groups"
              :key="group.id"
              :selected="selectedGroupIds.has(group.id)"
              @toggle="toggleGroup(group.id)"
            >
              {{ group.name }}
            </SelectionToggleButton>
          </div>
        </NeutralContainer>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: 'news-list' })">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="saving || !title.trim() || !contentMarkdown.trim()" @click="save">
            {{ saving ? t('common.loading') : t('common.save') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
