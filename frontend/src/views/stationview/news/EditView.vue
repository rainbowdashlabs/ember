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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type { MemberGroup, Role, UserTag } from '@/api/types'
import { news, memberGroups, stationMembers, userTags } from '@/api'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const newsId = computed(() => isEdit.value ? Number(route.params.id) : null)

const title = ref('')
const contentMarkdown = ref('')
const selectedRoleIds = ref<number[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const roles = ref<Role[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
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
    const [groupList, roleList, tagList] = await Promise.all([
      memberGroups.listGroups(),
      stationMembers.listAllRoles(),
      userTags.listTags(),
    ])
    groups.value = groupList
    roles.value = roleList
    tags.value = tagList
    if (newsId.value) {
      const entry = await news.getNews(newsId.value)
      title.value = entry.title
      contentMarkdown.value = entry.contentMarkdown
      selectedRoleIds.value = entry.roleIds ?? []
      selectedGroupIds.value = entry.groupIds ?? []
      selectedTagIds.value = entry.tagIds ?? []
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
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
      roleIds: selectedRoleIds.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
      memberIds: [] as number[],
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
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'news-list' })">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <div class="space-y-1">
            <FieldLabel>{{ t('news.titleField') }}</FieldLabel>
            <TextInput v-model="title" :placeholder="t('news.titlePlaceholder')" />
          </div>

          <div class="space-y-1">
            <div class="flex items-center justify-between">
              <FieldLabel>{{ t('news.content') }}</FieldLabel>
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

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('news.restrictToGroups') }}</SubHeader>
          <p class="text-xs text-(--text-muted)">{{ t('news.restrictHint') }}</p>
          <RestrictionPicker
              :roles="roles"
              :groups="groups"
              :tags="tags"
              :selected-role-ids="selectedRoleIds"
              :selected-group-ids="selectedGroupIds"
              :selected-tag-ids="selectedTagIds"
              @update:selected-role-ids="v => selectedRoleIds = v"
              @update:selected-group-ids="v => selectedGroupIds = v"
              @update:selected-tag-ids="v => selectedTagIds = v"
          />
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
