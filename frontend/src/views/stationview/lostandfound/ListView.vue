/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import {lostAndFound} from '@/api'
import type {LostAndFoundItem} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useAuthImages} from '@/composables/useAuthImage'
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
const {srcFor, load: loadImage, revokeAll} = useAuthImages<number>()

const showCreate = ref(false)

const {loading, error, reload} = useAsyncLoader(async () => {
  revokeAll()
  items.value = await lostAndFound.listItems()
  await Promise.all(items.value.filter(i => i.hasImage)
      .map(i => loadImage(i.id, `/lost-and-found/${i.id}/image`)))
})

const {running: creating, error: createError, run: createItem} = useAsyncAction(
    async (payload: LostItemCreatePayload) => {
      const item = await lostAndFound.createItem({description: payload.description, foundAt: payload.foundAt})
      if (payload.imageFile) {
        await lostAndFound.uploadImage(item.id, payload.imageFile)
      }
      showCreate.value = false
      await reload()
    },
    {formatError: () => t('common.error')},
)

const claim = useConfirmAction<number>({
  onConfirm: (itemId) => lostAndFound.claimItem(itemId),
  onSuccess: async () => {
    await reload()
    refreshSidebarCounts()
  },
  error,
})

const provided = useConfirmAction<number>({
  onConfirm: (itemId) => lostAndFound.markProvided(itemId),
  onSuccess: async () => {
    await reload()
    refreshSidebarCounts()
  },
  error,
})

const {running: confirming, run: runConfirm} = useAsyncAction(
    (confirm: () => Promise<void>) => confirm(),
)

const {error: deleteError, run: handleDelete} = useAsyncAction(
    async (itemId: number) => {
      await lostAndFound.deleteItem(itemId)
      await reload()
    },
    {formatError: () => t('common.error')},
)

const displayError = computed(() => error.value || createError.value || deleteError.value)
</script>

<template>
  <ViewContent
      :title="t('pages.lost-and-found.title')"
      :subtitle="t('pages.lost-and-found.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <PrimaryButton :icon="['fas', 'plus']" v-if="canCreate()" @click="showCreate = true">
          {{ t('lostAndFound.create') }}
        </PrimaryButton>
      </div>

      <Alert v-if="displayError" variant="error">{{ displayError }}</Alert>

      <AsyncSection
          :empty="items.length === 0"
          :empty-message="t('lostAndFound.empty')"
          :loading="loading"
      >
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <LostItemCard v-for="item in items" :key="item.id" :item="item"
                        :image-src="srcFor(item.id) ?? undefined"
                        :my-member-id="myMemberId()" :can-manage="canManage()"
                        @claim="claim.request" @provided="provided.request" @delete="handleDelete"/>
        </div>
      </AsyncSection>

      <LostItemConfirmModal v-model="claim.show.value" :title="t('lostAndFound.claimConfirmTitle')"
                            :message="t('lostAndFound.claimConfirmMessage')" :confirm-label="t('lostAndFound.claim')"
                            :loading="confirming" @confirm="runConfirm(claim.confirm)"/>

      <LostItemConfirmModal v-model="provided.show.value" :title="t('lostAndFound.providedConfirmTitle')"
                            :message="t('lostAndFound.providedConfirmMessage')"
                            :confirm-label="t('lostAndFound.provided')"
                            :loading="confirming" @confirm="runConfirm(provided.confirm)"/>

      <LostItemCreateModal v-model="showCreate" :creating="creating" @submit="createItem"/>
    </div>
  </ViewContent>
</template>
