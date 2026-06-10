/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { marked } from 'marked'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type { MemberGroup, UserTag } from '@/api/types'
import type { PartnerResponse } from '@/api/federation'
import { news, memberGroups, userTags, federation } from '@/api'
import FederationSharePicker from '@/components/input/FederationSharePicker.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
import { StationPermission } from '@/api/types'
const { loaded, hasPermission } = useSession()
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
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const publicBlog = ref(false)

// Federation sharing
const federationShared = ref(false)
const federationScope = ref('ALL_PARTNERS')
const federationVisibilityRole = ref('MEMBER')
const federationPartnerIds = ref<number[]>([])
const partners = ref<PartnerResponse[]>([])

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

    if (canFederateNews()) {
      if (federationShared.value) {
        const pIds = federationScope.value === 'SPECIFIC_PARTNERS' ? federationPartnerIds.value : undefined
        await news.setFederationShare(savedId, federationScope.value, federationVisibilityRole.value, pIds)
      } else {
        await news.removeFederationShare(savedId).catch(() => {})
      }
    }

    await router.push({ name: 'news-list' })
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
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
            <FieldLabel>{{ t('news.content') }}</FieldLabel>
            <MarkdownEditor v-model="contentMarkdown" :placeholder="t('news.contentPlaceholder')" />
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <div class="flex items-center justify-between">
            <div>
              <SubHeader>{{ t('news.publicBlog') }}</SubHeader>
              <p class="text-xs text-(--text-muted)">{{ t('news.publicBlogHint') }}</p>
            </div>
            <ToggleInput v-model="publicBlog"/>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('news.restrictToGroups') }}</SubHeader>
          <p class="text-xs text-(--text-muted)">{{ t('news.restrictHint') }}</p>
          <RestrictionPicker
              :groups="groups"
              :tags="tags"
              :selected-user-types="selectedUserTypes"
              :selected-group-ids="selectedGroupIds"
              :selected-tag-ids="selectedTagIds"
              @update:selected-user-types="v => selectedUserTypes = v"
              @update:selected-group-ids="v => selectedGroupIds = v"
              @update:selected-tag-ids="v => selectedTagIds = v"
          />
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('news.federation') }}</SubHeader>
          <p class="text-xs text-(--text-muted)">{{ t('news.federationShareHint') }}</p>
          <FederationSharePicker
              :shared="federationShared"
              :scope="federationScope"
              :partner-ids="federationPartnerIds"
              :partners="partners"
              :disabled="!canFederateNews()"
              :no-permission-hint="t('news.federationNoPermission')"
              @update:shared="federationShared = $event"
              @update:scope="federationScope = $event"
              @update:partner-ids="federationPartnerIds = $event"
          />
          <template v-if="federationShared && canFederateNews()">
            <div class="space-y-1">
              <FieldLabel>{{ t('news.federationVisibility') }}</FieldLabel>
              <SelectInput v-model="federationVisibilityRole">
                <option value="MEMBER">{{ t('news.visibilityAllMembers') }}</option>
                <option value="TEAM">{{ t('news.visibilityTeam') }}</option>
                <option value="MANAGER">{{ t('news.visibilityManager') }}</option>
              </SelectInput>
            </div>
          </template>
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
