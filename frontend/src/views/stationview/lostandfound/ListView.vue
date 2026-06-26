/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {lostAndFound} from '@/api'
import client from '@/api/client'
import type {LostAndFoundItem} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {StationPermission} from '@/api/types'
import LostItemCard from './listview/LostItemCard.vue'
import LostItemConfirmModal from './listview/LostItemConfirmModal.vue'
import LostItemCreateModal, {type LostItemCreatePayload} from './listview/LostItemCreateModal.vue'

const {t} = useI18n()
const {hasPermission, sessionInfo} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()
const canCreate = () => hasPermission(StationPermission.LOST_AND_FOUND_CREATE)
const canManage = () => hasPermission(StationPermission.LOST_AND_FOUND_MANAGE)
const myMemberId = () => sessionInfo.value?.member?.id

const items = ref<LostAndFoundItem[]>([])
const imageSrcs = ref<Record<number, string>>({})

const showCreate = ref(false)
const creating = ref(false)

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
  } catch {
    return
  }
}

const {loading, error, reload} = useAsyncLoader(async () => {
  revokeImages()
  items.value = await lostAndFound.listItems()
  await Promise.all(items.value.filter(i => i.hasImage).map(i => loadImageForItem(i.id)))
})

async function createItem(payload: LostItemCreatePayload) {
  creating.value = true
  error.value = ''
  try {
    const item = await lostAndFound.createItem({description: payload.description, foundAt: payload.foundAt})
    if (payload.imageFile) {
      await lostAndFound.uploadImage(item.id, payload.imageFile)
    }
    showCreate.value = false
    await reload()
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
    await reload()
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
    await reload()
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
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

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
        <LostItemCard v-for="item in items" :key="item.id" :item="item" :image-src="imageSrcs[item.id]"
                      :my-member-id="myMemberId()" :can-manage="canManage()"
                      @claim="askClaim" @provided="askProvided" @delete="handleDelete"/>
      </div>

      <LostItemConfirmModal v-model="showClaimConfirm" :title="t('lostAndFound.claimConfirmTitle')"
                            :message="t('lostAndFound.claimConfirmMessage')" :confirm-label="t('lostAndFound.claim')"
                            :loading="confirming" @confirm="confirmClaim"/>

      <LostItemConfirmModal v-model="showProvidedConfirm" :title="t('lostAndFound.providedConfirmTitle')"
                            :message="t('lostAndFound.providedConfirmMessage')"
                            :confirm-label="t('lostAndFound.provided')"
                            :loading="confirming" @confirm="confirmProvided"/>

      <LostItemCreateModal v-model="showCreate" :creating="creating" @submit="createItem"/>
    </div>
  </ViewContent>
</template>
