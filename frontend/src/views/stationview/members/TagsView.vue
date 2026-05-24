/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {UserTag, StationMember} from '@/api/types'
import {userTags, stationMembers} from '@/api'


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
    error.value = 'Fehler beim Laden der Daten.'
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
    error.value = 'Fehler beim Laden der Tag-Mitglieder.'
    tagMembers.value = []
  } finally {
    tagLoading.value = false
  }
}

function openCreateTag() {
  editingTag.value = null
  tagName.value = ''
  showTagModal.value = true
}

function openEditTag(tag: UserTag) {
  editingTag.value = tag
  tagName.value = tag.name ?? ''
  showTagModal.value = true
}

async function saveTag() {
  tagSaving.value = true
  error.value = ''
  try {
    if (editingTag.value) {
      await userTags.updateTag(editingTag.value.id, {name: tagName.value})
    } else {
      await userTags.createTag({name: tagName.value})
    }
    showTagModal.value = false
    tags.value = await userTags.listTags()
    if (selectedTag.value && editingTag.value?.id === selectedTag.value.id) {
      selectedTag.value = tags.value.find(t => t.id === selectedTag.value!.id) ?? null
    }
  } catch {
    error.value = 'Fehler beim Speichern des Tags.'
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
    error.value = 'Fehler beim Löschen des Tags.'
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
    error.value = 'Fehler beim Konvertieren des Tags zur Gruppe.'
  }
}

async function addMemberToTag(member: StationMember) {
  if (!selectedTag.value) return
  const newIds = [...tagMembers.value.map(m => m.id), member.id]
  try {
    await userTags.setTagMembers(selectedTag.value.id, newIds)
    tagMembers.value = await userTags.getTagMembers(selectedTag.value.id)
  } catch {
    error.value = 'Fehler beim Hinzufügen des Mitglieds.'
  }
}

async function removeMemberFromTag(member: StationMember) {
  if (!selectedTag.value) return
  const newIds = tagMembers.value.filter(m => m.id !== member.id).map(m => m.id)
  try {
    await userTags.setTagMembers(selectedTag.value.id, newIds)
    tagMembers.value = await userTags.getTagMembers(selectedTag.value.id)
  } catch {
    error.value = 'Fehler beim Entfernen des Mitglieds.'
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
            <SectionHeader>Tags</SectionHeader>
            <PrimaryButton @click="openCreateTag">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
              Tag erstellen
            </PrimaryButton>
          </div>

          <div v-if="tags.length === 0" class="text-center text-(--text-muted) py-8">
            Keine Tags vorhanden.
          </div>

          <div class="space-y-2">
            <NeutralContainer
                v-for="tag in tags"
                :key="tag.id"
                :class="selectedTag?.id === tag.id ? 'border-primary' : 'hover:border-primary'"
                class="flex items-center justify-between gap-2 flex-wrap cursor-pointer transition-colors"
                @click="selectTag(tag)"
            >
              <span class="font-medium">{{ tag.name }}</span>
              <div class="flex items-center gap-2">
                <SecondaryButton @click.stop="requestConvert(tag)">
                  <font-awesome-icon :icon="['fas', 'people-group']" class="mr-1"/>
                  Zu Gruppe
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
              <label class="block text-sm font-medium text-(--text-muted)">Mitglieder</label>
              <div v-if="tagMembers.length === 0" class="text-sm text-(--text-muted) py-2">
                Keine Mitglieder in diesem Tag.
              </div>
              <div class="space-y-1">
                <div v-for="member in tagMembers" :key="member.id"
                     class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
                  <div>
                    <MemberName :name="memberDisplayName(member)" :member-id="member.id" class="text-sm font-medium"/>
                    <div v-if="member.name && member.email" class="text-xs text-(--text-muted) ml-7">{{ member.email }}</div>
                  </div>
                  <IconButton :icon="['fas', 'xmark']" label="Entfernen" class="text-error hover:text-error/80 text-sm" @click="removeMemberFromTag(member)"/>
                </div>
              </div>
            </div>

            <!-- Available members to add -->
            <div class="space-y-1">
              <label class="block text-sm font-medium text-(--text-muted)">Mitglied hinzufügen</label>
              <div v-if="availableMembers.length === 0" class="text-sm text-(--text-muted) py-2">
                Alle Mitglieder sind bereits zugewiesen.
              </div>
              <div class="space-y-1">
                <div
                    v-for="member in availableMembers"
                    :key="member.id"
                    class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer transition-colors"
                    @click="addMemberToTag(member)"
                >
                  <div>
                    <MemberName :name="memberDisplayName(member)" :member-id="member.id" class="text-sm font-medium"/>
                    <div v-if="member.name && member.email" class="text-xs text-(--text-muted) ml-7">{{ member.email }}</div>
                  </div>
                  <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          Wähle einen Tag aus, um die Mitglieder zu verwalten.
        </div>
      </div>

      <!-- Create/Edit tag modal -->
      <Modal v-model="showTagModal">
        <div class="space-y-4">
          <SectionHeader>{{
              editingTag ? 'Tag bearbeiten' : 'Tag erstellen'
            }}
          </SectionHeader>
          <div class="space-y-1">
            <label class="block text-sm font-medium">Name</label>
            <TextInput v-model="tagName" placeholder="Tag-Name"/>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showTagModal = false">Abbrechen</SecondaryButton>
            <PrimaryButton :disabled="tagSaving || !tagName" @click="saveTag">
              {{ tagSaving ? 'Speichern...' : 'Speichern' }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete modal -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <p>Soll der Tag <strong>{{ deleteTarget?.name }}</strong> wirklich gelöscht werden?</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteModal = false">Abbrechen</SecondaryButton>
            <ErrorButton @click="confirmDelete">Löschen</ErrorButton>
          </div>
        </div>
      </Modal>

      <!-- Convert to group modal -->
      <Modal v-model="showConvertModal">
        <div class="space-y-4">
          <p>Soll der Tag <strong>{{ convertTarget?.name }}</strong> in eine Gruppe konvertiert werden? Der Tag wird dabei gelöscht.</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showConvertModal = false">Abbrechen</SecondaryButton>
            <PrimaryButton @click="confirmConvert">In Gruppe konvertieren</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
