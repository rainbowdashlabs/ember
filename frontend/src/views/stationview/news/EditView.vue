/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { marked } from 'marked'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {StationPermission, type MemberGroup, type UserTag} from '@/api/types'
import type { PartnerResponse } from '@/api/federation'
import { news, memberGroups, userTags, federation } from '@/api'
import ContentPanel from './editview/ContentPanel.vue'
import AttachmentsPanel from './editview/AttachmentsPanel.vue'
import {useNewsAttachments} from './editview/useNewsAttachments'
import PublicBlogPanel from './editview/PublicBlogPanel.vue'
import RestrictionsPanel from './editview/RestrictionsPanel.vue'
import FederationPanel from './editview/FederationPanel.vue'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const { loaded, hasPermission, sessionInfo } = useSession()
const canFederateNews = () => hasPermission(StationPermission.NEWS_FEDERATE)

const isEdit = computed(() => !!route.params.id)
const newsId = computed(() => isEdit.value ? Number(route.params.id) : null)

const title = ref('')
const contentMarkdown = ref('')
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])

const publicBlog = ref(false)

// Federation sharing
const federationShared = ref(false)
const federationScope = ref('ALL_PARTNERS')
const federationVisibilityRole = ref('MEMBER')
const federationPartnerIds = ref<number[]>([])
const partners = ref<PartnerResponse[]>([])

const stationUid = computed(() => sessionInfo.value?.stationId ?? '')
const {attachments, load: loadAttachments, add: addAttachment, remove: removeAttachment, move: moveAttachment, persist: persistAttachments} = useNewsAttachments()

const contentHtml = computed(() => {
  try {
    return marked.parse(contentMarkdown.value) as string
  } catch {
    return ''
  }
})

const { loading, error, reload } = useAsyncLoader(async () => {
  const [groupList, tagList] = await Promise.all([
    memberGroups.listGroups(),
    userTags.listTags(),
  ])
  groups.value = groupList
  tags.value = tagList
  if (canFederateNews()) {
    partners.value = (await federation.listPartners()).filter(p => p.partner.status === 'ACTIVE')
  }

  if (newsId.value) {
    const entry = await news.getNews(newsId.value)
    title.value = entry.title
    contentMarkdown.value = entry.contentMarkdown
    selectedUserTypes.value = entry.userTypes ?? []
    selectedGroupIds.value = entry.groupIds ?? []
    selectedTagIds.value = entry.tagIds ?? []
    publicBlog.value = entry.publicBlog ?? false
    loadAttachments(entry.attachments ?? [])

    if (canFederateNews()) {
      const fedShare = await news.getFederationShare(newsId.value)
      federationShared.value = fedShare.shared
      if (fedShare.shared) {
        federationScope.value = fedShare.scope ?? 'ALL_PARTNERS'
        federationVisibilityRole.value = fedShare.visibilityRole ?? 'MEMBER'
        federationPartnerIds.value = fedShare.partnerIds ?? []
      }
    }
  }
}, {autoLoad: false})

async function save() {
  if (!title.value.trim() || !contentMarkdown.value.trim()) return
  error.value = ''
  try {
    const data = {
      title: title.value,
      contentMarkdown: contentMarkdown.value,
      contentHtml: contentHtml.value,
      userTypes: selectedUserTypes.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
      memberIds: [] as number[],
      publicBlog: publicBlog.value,
    }
    let savedId: number
    if (newsId.value) {
      await news.updateNews(newsId.value, data)
      savedId = newsId.value
    } else {
      const created = await news.createNews(data)
      savedId = created.id
    }

    await persistAttachments(savedId)

    if (canFederateNews()) {
      if (federationShared.value) {
        const pIds = federationScope.value === 'SPECIFIC_PARTNERS' ? federationPartnerIds.value : undefined
        await news.setFederationShare(savedId, federationScope.value, federationVisibilityRole.value, pIds)
      } else {
        await news.removeFederationShare(savedId).catch(() => {})
      }
    }

    await router.push({ name: 'news-list' })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
}, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.news-create.title')"
      :subtitle="t('pages.news-create.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'news-list' })">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <ContentPanel v-model:title="title" v-model:content-markdown="contentMarkdown"/>
        <AttachmentsPanel
            v-model:attachments="attachments"
            :station-uid="stationUid"
            @add="addAttachment"
            @remove="removeAttachment"
            @move="moveAttachment"
        />
        <PublicBlogPanel v-model:public-blog="publicBlog"/>
        <RestrictionsPanel
            v-model:selected-user-types="selectedUserTypes"
            v-model:selected-group-ids="selectedGroupIds"
            v-model:selected-tag-ids="selectedTagIds"
            :groups="groups"
            :tags="tags"
        />
        <FederationPanel
            v-model:shared="federationShared"
            v-model:scope="federationScope"
            v-model:partner-ids="federationPartnerIds"
            v-model:visibility-role="federationVisibilityRole"
            :partners="partners"
            :can-federate="canFederateNews()"
        />

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: 'news-list' })">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :disabled="!title.trim() || !contentMarkdown.trim()" :action="save"/>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
