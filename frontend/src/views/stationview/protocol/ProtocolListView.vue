/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import { useSession } from '@/composables/useSession'
import { protocol } from '@/api'
import type { TestProtocol } from '@/api/protocol'

const { t } = useI18n()
const router = useRouter()
const { canManageProtocol, loaded } = useSession()

const protocols = ref<TestProtocol[]>([])
const loading = ref(true)
const error = ref('')

const showCreateModal = ref(false)
const newName = ref('')
const newDescription = ref('')
const newPassThreshold = ref<number | undefined>(undefined)

const showDeleteModal = ref(false)
const deleteTarget = ref<TestProtocol | null>(null)

async function loadData() {
  loading.value = true
  try {
    protocols.value = await protocol.listProtocols()
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function handleCreate() {
  if (!newName.value.trim()) return
  try {
    const created = await protocol.createProtocol({
      name: newName.value.trim(),
      description: newDescription.value,
      passThreshold: newPassThreshold.value,
    })
    showCreateModal.value = false
    newName.value = ''
    newDescription.value = ''
    newPassThreshold.value = undefined
    router.push({ name: 'protocol-detail', params: { id: created.id } })
  } catch { error.value = t('common.error') }
}

async function handleDelete() {
  if (!deleteTarget.value) return
  try {
    await protocol.deleteProtocol(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadData()
  } catch { error.value = t('common.error') }
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
onMounted(() => { if (loaded.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('protocol.title') }}</SectionHeader>
      <PrimaryButton v-if="canManageProtocol()" @click="showCreateModal = true">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.create') }}
      </PrimaryButton>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div v-if="!loading && protocols.length === 0" class="text-center text-[var(--text-muted)] py-8">
      {{ t('protocol.empty') }}
    </div>

    <div class="space-y-2">
      <NeutralContainer
        v-for="p in protocols"
        :key="p.id"
        class="flex items-center gap-3 cursor-pointer hover:border-[var(--primary)] transition-colors group"
        @click="router.push({ name: 'protocol-detail', params: { id: p.id } })"
      >
        <div class="flex-1 min-w-0">
          <div class="font-medium">{{ p.name }}</div>
          <div v-if="p.description" class="text-sm text-[var(--text-muted)] truncate">{{ p.description }}</div>
        </div>
        <span v-if="p.passThreshold" class="text-xs text-[var(--text-muted)]">{{ t('protocol.threshold') }}: {{ p.passThreshold }}P</span>
        <div v-if="canManageProtocol()" class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <EditButton :label="t('common.edit')" @click.stop="router.push({ name: 'protocol-detail', params: { id: p.id } })" />
          <DeleteButton :label="t('common.delete')" @click.stop="deleteTarget = p; showDeleteModal = true" />
        </div>
      </NeutralContainer>
    </div>

    <!-- Create Modal -->
    <Modal v-model="showCreateModal">
      <h3 class="text-lg font-semibold mb-3">{{ t('protocol.create') }}</h3>
      <form @submit.prevent="handleCreate" class="space-y-3">
        <TextInput v-model="newName" :placeholder="t('protocol.name')" required />
        <TextAreaInput v-model="newDescription" :placeholder="t('protocol.description')" />
        <div>
          <label class="block text-sm font-medium mb-1">{{ t('protocol.passThreshold') }}</label>
          <NumberInput v-model="newPassThreshold" :placeholder="t('protocol.passThresholdHint')" />
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('protocol.create') }}</PrimaryButton>
        </div>
      </form>
    </Modal>

    <!-- Delete Modal -->
    <Modal v-model="showDeleteModal">
      <h3 class="text-lg font-semibold mb-3">{{ t('protocol.deleteConfirm') }}</h3>
      <p class="mb-4">{{ deleteTarget?.name }}</p>
      <div class="flex gap-2 justify-end">
        <PrimaryButton @click="handleDelete">{{ t('common.delete') }}</PrimaryButton>
      </div>
    </Modal>
  </ViewContent>
</template>
