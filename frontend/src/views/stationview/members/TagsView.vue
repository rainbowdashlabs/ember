/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {UserTag, StationMember} from '@/api/types'
import {userTags, stationMembers} from '@/api'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()


const tags = ref<UserTag[]>([])
const allMembers = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')

// Selected tag
const selectedTag = ref<UserTag | null>(null)
const tagMembers = ref<StationMember[]>([])
const tagLoading = ref(false)

// Create/Edit modal
const showTagModal = ref(false)
const editingTag = ref<UserTag | null>(null)
const tagName = ref('')
const tagColor = ref('')
const tagVisible = ref(false)
const tagPosition = ref(0)
const tagSaving = ref(false)

// Delete modal
const showDeleteModal = ref(false)
const deleteTarget = ref<UserTag | null>(null)

// Convert to group modal
const showConvertModal = ref(false)
const convertTarget = ref<UserTag | null>(null)

const availableMembers = computed(() => {
  const memberIds = new Set(tagMembers.value.map(m => m.id))
  return allMembers.value.filter(m => !memberIds.has(m.id))
})

function memberDisplayName(member: StationMember): string {
  if (member.name && member.name.trim()) return member.name
  return member.email ?? `#${member.id}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [t, m] = await Promise.all([
      userTags.listTags(),
      stationMembers.listMembers(),
    ])
    tags.value = t
    allMembers.value = m
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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

function requestDelete(tag: UserTag) {
  deleteTarget.value = tag
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await userTags.deleteTag(deleteTarget.value.id)
    showDeleteModal.value = false
    if (selectedTag.value?.id === deleteTarget.value.id) {
      selectedTag.value = null
      tagMembers.value = []
    }
    deleteTarget.value = null
    tags.value = await userTags.listTags()
  } catch {
    error.value = t('common.error')
  }
}

function requestConvert(tag: UserTag) {
  convertTarget.value = tag
  showConvertModal.value = true
}

async function confirmConvert() {
  if (!convertTarget.value) return
  try {
    await userTags.convertToGroup(convertTarget.value.id)
    showConvertModal.value = false
    if (selectedTag.value?.id === convertTarget.value.id) {
      selectedTag.value = null
      tagMembers.value = []
    }
    convertTarget.value = null
    tags.value = await userTags.listTags()
  } catch {
    error.value = t('common.error')
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

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <!-- Tags list -->
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionHeader>{{ t('userTags.title') }}</SectionHeader>
            <PrimaryButton :icon="['fas', 'plus']" @click="openCreateTag">
              {{ t('userTags.create') }}
            </PrimaryButton>
          </div>

          <EmptyState v-if="tags.length === 0">{{ t('userTags.empty') }}</EmptyState>

          <div class="space-y-2">
            <NeutralContainer
                v-for="tag in tags"
                :key="tag.id"
                :class="selectedTag?.id === tag.id ? 'border-primary' : 'hover:border-primary'"
                class="flex items-center justify-between gap-2 flex-wrap cursor-pointer transition-colors"
                @click="selectTag(tag)"
            >
              <span class="flex items-center gap-2">
                <span v-if="tag.color" class="inline-block h-3 w-3 rounded-full" :style="{ backgroundColor: tag.color }"></span>
                <span class="font-medium">{{ tag.name }}</span>
                <font-awesome-icon v-if="tag.visible" :icon="['fas', 'eye']" class="text-xs text-(--text-muted)" />
              </span>
              <div class="flex items-center gap-2">
                <SecondaryButton :icon="['fas', 'people-group']" @click.stop="requestConvert(tag)">
                  {{ t('userTags.convertToGroup') }}
                </SecondaryButton>
                <EditButton @click.stop="openEditTag(tag)"/>
                <DeleteButton @click.stop="requestDelete(tag)"/>
              </div>
            </NeutralContainer>
          </div>
        </div>

        <!-- Tag members panel -->
        <div v-if="selectedTag" class="space-y-4">
          <SectionHeader>{{ selectedTag.name }}</SectionHeader>

          <Spinner v-if="tagLoading" size="md"/>

          <template v-if="!tagLoading">
            <!-- Current members -->
            <div class="space-y-1">
              <FieldLabel class="text-(--text-muted)">{{ t('userTags.currentMembers') }}</FieldLabel>
              <MutedText tag="div" size="sm" class="py-2" v-if="tagMembers.length === 0">
                {{ t('userTags.noMembers') }}
              </MutedText>
              <div class="space-y-1">
                <div v-for="member in tagMembers" :key="member.id"
                     class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
                  <div>
                    <MemberName :identity="member.identity" class="text-sm font-medium"/>
                    <div v-if="member.name && member.email" class="text-xs text-(--text-muted) ml-7">{{ member.email }}</div>
                  </div>
                  <IconButton :icon="['fas', 'xmark']" :label="t('userTags.removeMember')" class="text-error hover:text-error/80 text-sm" @click="removeMemberFromTag(member)"/>
                </div>
              </div>
            </div>

            <!-- Available members to add -->
            <div class="space-y-1">
              <FieldLabel class="text-(--text-muted)">{{ t('userTags.addMembers') }}</FieldLabel>
              <MutedText tag="div" size="sm" class="py-2" v-if="availableMembers.length === 0">
                {{ t('userTags.allAdded') }}
              </MutedText>
              <div class="space-y-1">
                <div
                    v-for="member in availableMembers"
                    :key="member.id"
                    class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer transition-colors"
                    @click="addMemberToTag(member)"
                >
                  <div>
                    <MemberName :identity="member.identity" class="text-sm font-medium"/>
                    <div v-if="member.name && member.email" class="text-xs text-(--text-muted) ml-7">{{ member.email }}</div>
                  </div>
                  <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('userTags.selectHint') }}
        </div>
      </div>

      <!-- Create/Edit tag modal -->
      <Modal v-model="showTagModal">
        <div class="space-y-4">
          <SectionHeader>{{
              editingTag ? t('userTags.editTitle') : t('userTags.createTitle')
            }}
          </SectionHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('userTags.name') }}</FieldLabel>
            <TextInput v-model="tagName" :placeholder="t('userTags.namePlaceholder')"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('userTags.color') }}</FieldLabel>
            <div class="flex items-center gap-2">
              <ColorInput v-model="tagColor" />
              <SecondaryButton v-if="tagColor" compact @click="tagColor = ''">
                <font-awesome-icon :icon="['fas', 'xmark']" />
              </SecondaryButton>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <FieldLabel>{{ t('userTags.visible') }}</FieldLabel>
            <ToggleInput v-model="tagVisible" />
          </div>
          <MutedText size="sm">{{ t('userTags.visibleHint') }}</MutedText>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showTagModal = false">{{ t('userTags.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="tagSaving || !tagName" @click="saveTag">
              {{ tagSaving ? t('common.loading') : t('userTags.save') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete modal -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <p>{{ t('userTags.deleteConfirmDetail', {name: deleteTarget?.name}) }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteModal = false">{{ t('userTags.cancel') }}</SecondaryButton>
            <ErrorButton @click="confirmDelete">{{ t('userTags.delete') }}</ErrorButton>
          </div>
        </div>
      </Modal>

      <!-- Convert to group modal -->
      <Modal v-model="showConvertModal">
        <div class="space-y-4">
          <p>{{ t('userTags.convertConfirmDetail', {name: convertTarget?.name}) }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showConvertModal = false">{{ t('userTags.cancel') }}</SecondaryButton>
            <PrimaryButton @click="confirmConvert">{{ t('userTags.convertToGroup') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
