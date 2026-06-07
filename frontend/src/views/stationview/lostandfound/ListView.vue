/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import {lostAndFound} from '@/api'
import client from '@/api/client'
import type {LostAndFoundItem} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
const {hasPermission, sessionInfo} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()
const canCreate = () => hasPermission(StationPermission.LOST_AND_FOUND_CREATE)
const canManage = () => hasPermission(StationPermission.LOST_AND_FOUND_MANAGE)
const myMemberId = () => sessionInfo.value?.member?.id

const items = ref<LostAndFoundItem[]>([])
const imageSrcs = ref<Record<number, string>>({})
const loading = ref(true)
const error = ref('')

// -- Create form --
const showCreate = ref(false)
const creating = ref(false)
const newDescription = ref('')
const newFoundAt = ref(new Date().toISOString().substring(0, 10))
const newImageFile = ref<File | null>(null)
const newImagePreview = ref<string | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const cameraInputRef = ref<HTMLInputElement | null>(null)

// -- Confirmation modals --
const showClaimConfirm = ref(false)
const showProvidedConfirm = ref(false)
const confirmItemId = ref<number | null>(null)
const confirming = ref(false)

function revokeImages() {
  for (const url of Object.values(imageSrcs.value)) {
    URL.revokeObjectURL(url)
  }
  imageSrcs.value = {}
}

async function loadImageForItem(id: number) {
  try {
    const res = await client.get(`/lost-and-found/${id}/image`, {
      responseType: 'blob',
      validateStatus: (status) => status === 200 || status === 404,
    })
    if (res.status === 200 && res.data) {
      imageSrcs.value[id] = URL.createObjectURL(res.data)
    }
  } catch { /* no image */ }
}

async function loadItems() {
  loading.value = true
  revokeImages()
  try {
    items.value = await lostAndFound.listItems()
    await Promise.all(items.value.filter(i => i.hasImage).map(i => loadImageForItem(i.id)))
  } catch {
    error.value = t('common.error')
  }
  loading.value = false
}

function handleImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  newImageFile.value = file
  if (newImagePreview.value) URL.revokeObjectURL(newImagePreview.value)
  newImagePreview.value = URL.createObjectURL(file)
  input.value = ''
}

function clearNewImage() {
  newImageFile.value = null
  if (newImagePreview.value) {
    URL.revokeObjectURL(newImagePreview.value)
    newImagePreview.value = null
  }
}

function resetCreateForm() {
  newDescription.value = ''
  newFoundAt.value = new Date().toISOString().substring(0, 10)
  clearNewImage()
}

async function createItem() {
  creating.value = true
  error.value = ''
  try {
    const item = await lostAndFound.createItem({description: newDescription.value, foundAt: newFoundAt.value})
    if (newImageFile.value) {
      await lostAndFound.uploadImage(item.id, newImageFile.value)
    }
    showCreate.value = false
    resetCreateForm()
    await loadItems()
  } catch {
    error.value = t('common.error')
  }
  creating.value = false
}

function askClaim(itemId: number) {
  confirmItemId.value = itemId
  showClaimConfirm.value = true
}

async function confirmClaim() {
  if (confirmItemId.value == null) return
  confirming.value = true
  error.value = ''
  try {
    await lostAndFound.claimItem(confirmItemId.value)
    showClaimConfirm.value = false
    await loadItems()
    refreshSidebarCounts()
  } catch {
    error.value = t('common.error')
  }
  confirming.value = false
}

function askProvided(itemId: number) {
  confirmItemId.value = itemId
  showProvidedConfirm.value = true
}

async function confirmProvided() {
  if (confirmItemId.value == null) return
  confirming.value = true
  error.value = ''
  try {
    await lostAndFound.markProvided(confirmItemId.value)
    showProvidedConfirm.value = false
    await loadItems()
    refreshSidebarCounts()
  } catch {
    error.value = t('common.error')
  }
  confirming.value = false
}

async function handleDelete(itemId: number) {
  error.value = ''
  try {
    await lostAndFound.deleteItem(itemId)
    await loadItems()
  } catch {
    error.value = t('common.error')
  }
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '–'
  return new Date(dateStr).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}

onMounted(loadItems)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('lostAndFound.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']" v-if="canCreate()" @click="showCreate = true">
          {{ t('lostAndFound.create') }}
        </PrimaryButton>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Spinner v-if="loading" size="lg"/>

      <EmptyState v-if="!loading && items.length === 0">{{ t('lostAndFound.empty') }}</EmptyState>

      <div v-if="!loading" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <NeutralContainer v-for="item in items" :key="item.id" class="space-y-3"
                          :class="{'opacity-60': item.claimedBy && !canManage()}">
          <img v-if="imageSrcs[item.id]"
               :src="imageSrcs[item.id]"
               :alt="item.description ?? t('lostAndFound.noDescription')"
               class="w-full h-48 object-cover rounded-lg"/>
          <div v-else-if="!item.hasImage" class="w-full h-48 bg-(--bg-accent) rounded-lg flex items-center justify-center">
            <font-awesome-icon :icon="['fas', 'box-open']" class="text-4xl text-(--text-muted)"/>
          </div>

          <div>
            <p class="text-sm font-medium">{{ item.description || t('lostAndFound.noDescription') }}</p>
            <p class="text-xs text-(--text-muted)">{{ t('lostAndFound.foundAt') }}: {{ formatDate(item.foundAt) }}</p>
          </div>

          <!-- Claimed state -->
          <div v-if="item.claimedBy" class="space-y-2">
            <SuccessBadge v-if="item.claimedBy === myMemberId()">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('lostAndFound.claimedByYou') }}
            </SuccessBadge>
            <SuccessBadge v-else>
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('lostAndFound.claimedBy', {name: item.claimedByName ?? '?'}) }}
            </SuccessBadge>

            <div v-if="canManage()" class="flex items-center gap-2">
              <SuccessButton :icon="['fas', 'circle-check']" class="flex-1 text-sm" @click="askProvided(item.id)">
                {{ t('lostAndFound.provided') }}
              </SuccessButton>
              <DeleteButton @click="handleDelete(item.id)"/>
            </div>
          </div>

          <!-- Unclaimed state -->
          <div v-else class="flex items-center gap-2">
            <SuccessButton class="flex-1 text-sm" @click="askClaim(item.id)">
              {{ t('lostAndFound.claim') }}
            </SuccessButton>
            <DeleteButton v-if="canManage()" @click="handleDelete(item.id)"/>
          </div>
        </NeutralContainer>
      </div>

      <!-- Claim confirmation modal -->
      <Modal v-model="showClaimConfirm">
        <div class="space-y-4 p-4">
          <SubHeader>{{ t('lostAndFound.claimConfirmTitle') }}</SubHeader>
          <p class="text-sm text-(--text-muted)">{{ t('lostAndFound.claimConfirmMessage') }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showClaimConfirm = false">{{ t('common.cancel') }}</SecondaryButton>
            <SuccessButton :disabled="confirming" @click="confirmClaim">
              {{ confirming ? t('common.loading') : t('lostAndFound.claim') }}
            </SuccessButton>
          </div>
        </div>
      </Modal>

      <!-- Provided confirmation modal -->
      <Modal v-model="showProvidedConfirm">
        <div class="space-y-4 p-4">
          <SubHeader>{{ t('lostAndFound.providedConfirmTitle') }}</SubHeader>
          <p class="text-sm text-(--text-muted)">{{ t('lostAndFound.providedConfirmMessage') }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showProvidedConfirm = false">{{ t('common.cancel') }}</SecondaryButton>
            <SuccessButton :disabled="confirming" @click="confirmProvided">
              {{ confirming ? t('common.loading') : t('lostAndFound.provided') }}
            </SuccessButton>
          </div>
        </div>
      </Modal>

      <!-- Create modal -->
      <Modal v-model="showCreate">
        <div class="space-y-4 p-4">
          <SubHeader>{{ t('lostAndFound.createTitle') }}</SubHeader>

          <div class="space-y-2">
            <FieldLabel>{{ t('lostAndFound.image') }}</FieldLabel>
            <div v-if="newImagePreview" class="relative">
              <img :src="newImagePreview" alt="" class="w-full max-h-48 object-cover rounded-lg"/>
              <IconButton
                :icon="['fas', 'xmark']"
                label="Remove image"
                class="absolute top-2 right-2 bg-error text-error-text rounded-full h-6 w-6 hover:bg-error/80"
                @click="clearNewImage"
              />
            </div>
            <div v-else class="flex gap-2">
              <SecondaryButton :icon="['fas', 'upload']" class="flex-1" @click="fileInputRef?.click()">
                {{ t('lostAndFound.uploadImage') }}
              </SecondaryButton>
              <SecondaryButton :icon="['fas', 'camera']" class="flex-1" @click="cameraInputRef?.click()">
                {{ t('lostAndFound.takePhoto') }}
              </SecondaryButton>
            </div>
            <input ref="fileInputRef" type="file" accept="image/png,image/jpeg,image/webp" class="hidden"
                   @change="handleImageSelected"/>
            <input ref="cameraInputRef" type="file" accept="image/*" capture="environment" class="hidden"
                   @change="handleImageSelected"/>
          </div>

          <div class="space-y-1">
            <FieldLabel>{{ t('lostAndFound.description') }}</FieldLabel>
            <TextAreaInput v-model="newDescription" :placeholder="t('lostAndFound.descriptionPlaceholder')"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('lostAndFound.foundAt') }}</FieldLabel>
            <DateInput v-model="newFoundAt"/>
          </div>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showCreate = false; resetCreateForm()">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="creating" @click="createItem">
              {{ creating ? t('common.loading') : t('lostAndFound.create') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
