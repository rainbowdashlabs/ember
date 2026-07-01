/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {StationMember, UserTag} from '@/api/types'
import {stationMembers, userTags} from '@/api'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import TagListPanel from './tagsview/TagListPanel.vue'
import TagMembersPanel from './tagsview/TagMembersPanel.vue'
import TagFormModal from './tagsview/TagFormModal.vue'
import TagDeleteModal from './tagsview/TagDeleteModal.vue'
import TagConvertModal from './tagsview/TagConvertModal.vue'

const {t} = useI18n()
const {hasPermission} = useSession()
const canConvertToGroup = computed(() => hasPermission(StationPermission.MEMBER_MANAGE_GROUP))

const tags = ref<UserTag[]>([])
const allMembers = ref<StationMember[]>([])

const selectedTag = ref<UserTag | null>(null)
const tagMembers = ref<StationMember[]>([])
const tagLoading = ref(false)

const showTagModal = ref(false)
const editingTag = ref<UserTag | null>(null)
const tagName = ref('')
const tagColor = ref('')
const tagVisible = ref(false)
const tagPosition = ref(0)
const tagSaving = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  const [tagList, members] = await Promise.all([
    userTags.listTags(),
    stationMembers.listMembers(),
  ])
  tags.value = tagList
  allMembers.value = members
})

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<UserTag>({
  onDelete: tag => userTags.deleteTag(tag.id),
  onSuccess: async deleted => {
    if (selectedTag.value?.id === deleted.id) {
      selectedTag.value = null
      tagMembers.value = []
    }
    tags.value = await userTags.listTags()
  },
  error,
})

const {
  show: showConvertModal,
  target: convertTarget,
  request: requestConvert,
  confirm: confirmConvert,
} = useConfirmAction<UserTag>({
  onConfirm: tag => userTags.convertToGroup(tag.id),
  onSuccess: async converted => {
    if (selectedTag.value?.id === converted.id) {
      selectedTag.value = null
      tagMembers.value = []
    }
    tags.value = await userTags.listTags()
  },
  error,
})

const availableMembers = computed(() => {
  const memberIds = new Set(tagMembers.value.map(m => m.id))
  return allMembers.value.filter(m => !memberIds.has(m.id))
})

async function selectTag(tag: UserTag) {
  selectedTag.value = tag
  tagLoading.value = true
  try {
    tagMembers.value = await userTags.getTagMembers(tag.id)
  } catch {
    error.value = t('common.error')
    tagMembers.value = []
  } finally {
    tagLoading.value = false
  }
}

function openCreateTag() {
  editingTag.value = null
  tagName.value = ''
  tagColor.value = ''
  tagVisible.value = false
  tagPosition.value = 0
  showTagModal.value = true
}

function openEditTag(tag: UserTag) {
  editingTag.value = tag
  tagName.value = tag.name ?? ''
  tagColor.value = tag.color ?? ''
  tagVisible.value = tag.visible ?? false
  tagPosition.value = tag.position ?? 0
  showTagModal.value = true
}

async function saveTag() {
  tagSaving.value = true
  error.value = ''
  try {
    if (editingTag.value) {
      await userTags.updateTag(editingTag.value.id, {name: tagName.value, color: tagColor.value || null, visible: tagVisible.value, position: tagPosition.value})
    } else {
      await userTags.createTag({name: tagName.value, color: tagColor.value || null, visible: tagVisible.value, position: tagPosition.value})
    }
    showTagModal.value = false
    tags.value = await userTags.listTags()
    if (selectedTag.value && editingTag.value?.id === selectedTag.value.id) {
      selectedTag.value = tags.value.find(t => t.id === selectedTag.value!.id) ?? null
    }
  } catch {
    error.value = t('common.error')
  } finally {
    tagSaving.value = false
  }
}

async function addMemberToTag(member: StationMember) {
  if (!selectedTag.value) return
  const newIds = [...tagMembers.value.map(m => m.id), member.id]
  try {
    await userTags.setTagMembers(selectedTag.value.id, newIds)
    tagMembers.value = await userTags.getTagMembers(selectedTag.value.id)
  } catch {
    error.value = t('common.error')
  }
}

async function removeMemberFromTag(member: StationMember) {
  if (!selectedTag.value) return
  const newIds = tagMembers.value.filter(m => m.id !== member.id).map(m => m.id)
  try {
    await userTags.setTagMembers(selectedTag.value.id, newIds)
    tagMembers.value = await userTags.getTagMembers(selectedTag.value.id)
  } catch {
    error.value = t('common.error')
  }
}

</script>

<template>
  <ViewContent
      :title="t('pages.members-tags.title')"
      :subtitle="t('pages.members-tags.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <TagListPanel :tags="tags" :selected-tag="selectedTag" :can-convert-to-group="canConvertToGroup"
                      @create="openCreateTag" @select="selectTag" @edit="openEditTag"
                      @delete="requestDelete" @convert="requestConvert"/>
        <TagMembersPanel v-if="selectedTag" :selected-tag="selectedTag" :tag-loading="tagLoading"
                         :tag-members="tagMembers" :available-members="availableMembers"
                         @add-member="addMemberToTag" @remove-member="removeMemberFromTag"/>
        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('userTags.selectHint') }}
        </div>
      </div>

      <TagFormModal v-model="showTagModal" :is-edit="!!editingTag" v-model:name="tagName"
                    v-model:color="tagColor" v-model:visible="tagVisible" :saving="tagSaving" @save="saveTag"/>
      <TagDeleteModal v-model="showDeleteModal" :target="deleteTarget" @confirm="confirmDelete"/>
      <TagConvertModal v-model="showConvertModal" :target="convertTarget" @confirm="confirmConvert"/>
    </div>
  </ViewContent>
</template>
