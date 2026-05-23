/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {InventoryBlock} from '@/api/lending'
import * as lending from '@/api/lending'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

const blocks = ref<InventoryBlock[]>([])
const loading = ref(true)
const error = ref('')

const showCreateModal = ref(false)
const newBlockFrom = ref('')
const newBlockTo = ref('')
const newBlockReason = ref('')
const saving = ref(false)

async function loadBlocks() {
  loading.value = true
  error.value = ''
  try {
    blocks.value = await lending.listBlocks()
  } catch {
    error.value = t('lending.loadError')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (loaded.value) loadBlocks()
})

watch(loaded, (v) => {
  if (v) loadBlocks()
})

async function handleCreate() {
  if (!newBlockFrom.value || !newBlockTo.value) return
  saving.value = true
  try {
    await lending.createBlock({
      blockFrom: newBlockFrom.value,
      blockTo: newBlockTo.value,
      reason: newBlockReason.value,
    })
    showCreateModal.value = false
    newBlockFrom.value = ''
    newBlockTo.value = ''
    newBlockReason.value = ''
    await loadBlocks()
  } catch { /* ignore */ } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await lending.deleteBlock(id)
    await loadBlocks()
  } catch { /* ignore */ }
}

function formatDate(d: string): string {
  return new Date(d).toLocaleDateString('de-DE')
}
</script>

<template>
  <ViewContent>
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 mb-4">
      <SectionHeader>{{ t('lending.blocksTitle') }}</SectionHeader>
      <div class="flex gap-2">
        <SecondaryButton @click="router.push({name: 'inventory-lending'})">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1"/>
          {{ t('lending.backToList') }}
        </SecondaryButton>
        <PrimaryButton @click="showCreateModal = true">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
          {{ t('lending.addBlock') }}
        </PrimaryButton>
      </div>
    </div>

    <Spinner v-if="loading"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>

    <p v-else-if="blocks.length === 0" class="text-sm text-[var(--text-muted)]">{{ t('lending.noBlocks') }}</p>

    <div v-else class="flex flex-col gap-2">
      <NeutralContainer v-for="block in blocks" :key="block.id">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <span class="font-medium">{{ formatDate(block.blockFrom) }} - {{ formatDate(block.blockTo) }}</span>
            <span v-if="block.reason" class="text-sm text-[var(--text-muted)] ml-2">{{ block.reason }}</span>
          </div>
          <DeleteButton @click="handleDelete(block.id)"/>
        </div>
      </NeutralContainer>
    </div>

    <!-- Create modal -->
    <Modal v-model="showCreateModal">
        <SectionHeader class="mb-4">{{ t('lending.addBlock') }}</SectionHeader>
        <div class="flex flex-col gap-3">
          <div>
            <label class="text-sm font-medium mb-1 block">{{ t('lending.blockFrom') }}</label>
            <DateInput v-model="newBlockFrom"/>
          </div>
          <div>
            <label class="text-sm font-medium mb-1 block">{{ t('lending.blockTo') }}</label>
            <DateInput v-model="newBlockTo"/>
          </div>
          <div>
            <label class="text-sm font-medium mb-1 block">{{ t('lending.blockReason') }}</label>
            <TextInput v-model="newBlockReason" :placeholder="t('lending.blockReasonPlaceholder')"/>
          </div>
          <div class="flex justify-end gap-2 mt-2">
            <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="saving || !newBlockFrom || !newBlockTo" @click="handleCreate">
              {{ t('common.save') }}
            </PrimaryButton>
          </div>
        </div>
    </Modal>
  </ViewContent>
</template>
