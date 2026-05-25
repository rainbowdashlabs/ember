/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { QuizCategory } from '@/api/types'
import { quiz } from '@/api'

const props = defineProps<{
  categories: QuizCategory[]
}>()

const emit = defineEmits<{
  updated: []
}>()

const { t } = useI18n()

// Create category
const showCategoryModal = ref(false)
const newCategoryName = ref('')
const newCategoryDescription = ref('')

// Edit category
const showEditCategoryModal = ref(false)
const editCategoryId = ref<number | null>(null)
const editCategoryName = ref('')
const editCategoryDescription = ref('')

// Delete category
const showDeleteCategoryModal = ref(false)
const categoryToDelete = ref<QuizCategory | null>(null)

function openCategoryModal() {
  newCategoryName.value = ''
  newCategoryDescription.value = ''
  showCategoryModal.value = true
}

async function createCategory() {
  if (!newCategoryName.value.trim()) return
  await quiz.createCategory({ name: newCategoryName.value.trim(), description: newCategoryDescription.value.trim() })
  showCategoryModal.value = false
  emit('updated')
}

function openEditCategoryModal(category: QuizCategory) {
  editCategoryId.value = category.id
  editCategoryName.value = category.name
  editCategoryDescription.value = category.description || ''
  showEditCategoryModal.value = true
}

async function updateCategory() {
  if (!editCategoryId.value || !editCategoryName.value.trim()) return
  await quiz.updateCategory(editCategoryId.value, { name: editCategoryName.value.trim(), description: editCategoryDescription.value.trim() })
  showEditCategoryModal.value = false
  editCategoryId.value = null
  emit('updated')
}

function confirmDeleteCategory(category: QuizCategory) {
  categoryToDelete.value = category
  showDeleteCategoryModal.value = true
}

async function deleteCategory() {
  if (!categoryToDelete.value) return
  await quiz.deleteCategory(categoryToDelete.value.id)
  showDeleteCategoryModal.value = false
  categoryToDelete.value = null
  emit('updated')
}
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('quiz.categories.title') }}</SectionHeader>
      <SecondaryButton :icon="['fas', 'plus']" @click="openCategoryModal">
        {{ t('quiz.categories.create') }}
      </SecondaryButton>
    </div>
    <EmptyState compact v-if="categories.length === 0">{{ t('quiz.categories.noCategories') }}</EmptyState>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
      <NeutralContainer v-for="category in categories" :key="category.id" class="!p-2">
        <div class="flex items-center gap-2">
          <div class="flex-1 min-w-0">
            <span class="text-sm">{{ category.name }}</span>
            <p v-if="category.description" class="text-xs text-(--text-muted)">{{ category.description }}</p>
          </div>
          <EditButton @click="openEditCategoryModal(category)" />
          <DeleteButton @click="confirmDeleteCategory(category)" />
        </div>
      </NeutralContainer>
    </div>
  </div>

  <!-- Category Create Modal -->
  <Modal v-model="showCategoryModal">
    <div class="space-y-4">
      <SubHeader>{{ t('quiz.categories.create') }}</SubHeader>
      <TextInput v-model="newCategoryName" :placeholder="t('quiz.categories.name')" />
      <TextAreaInput v-model="newCategoryDescription" :placeholder="t('quiz.categories.description')" />
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showCategoryModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!newCategoryName.trim()" @click="createCategory">{{ t('common.save') }}</PrimaryButton>
      </div>
    </div>
  </Modal>

  <!-- Category Edit Modal -->
  <Modal v-model="showEditCategoryModal">
    <div class="space-y-4">
      <SubHeader>{{ t('quiz.categories.edit') }}</SubHeader>
      <TextInput v-model="editCategoryName" :placeholder="t('quiz.categories.name')" />
      <TextAreaInput v-model="editCategoryDescription" :placeholder="t('quiz.categories.description')" />
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showEditCategoryModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!editCategoryName.trim()" @click="updateCategory">{{ t('common.save') }}</PrimaryButton>
      </div>
    </div>
  </Modal>

  <!-- Category Delete Modal -->
  <Modal v-model="showDeleteCategoryModal">
    <div class="space-y-4">
      <SubHeader>{{ t('common.delete') }}</SubHeader>
      <p class="text-sm">{{ t('quiz.categories.deleteConfirm') }}</p>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showDeleteCategoryModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton @click="deleteCategory">{{ t('common.delete') }}</ErrorButton>
      </div>
    </div>
  </Modal>
</template>
