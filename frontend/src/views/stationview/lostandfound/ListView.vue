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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import {lostAndFound, managedMembers as managedMembersApi} from '@/api'
import type {LostAndFoundItem} from '@/api/lostAndFound'
import type {ManagedMember} from '@/api/managedMembers'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useAuthImages} from '@/composables/useAuthImage'
import {StationPermission} from '@/api/types'
import {apiErrorMessage} from '@/util/apiError'
import {describeFailure, type Failure} from '@/util/failure'
import {UnreadableImageError} from '@/util/imageUpload'
import LostItemCard from './listview/LostItemCard.vue'
import LostItemClaimModal from './listview/LostItemClaimModal.vue'
import LostItemConfirmModal from './listview/LostItemConfirmModal.vue'
import LostItemCreateModal, {type LostItemCreatePayload} from './listview/LostItemCreateModal.vue'

const {t} = useI18n()
const {hasPermission, sessionInfo} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()
const canCreate = () => hasPermission(StationPermission.LOST_AND_FOUND_CREATE)
const canManage = () => hasPermission(StationPermission.LOST_AND_FOUND_MANAGE)
const myMemberId = () => sessionInfo.value?.member?.id

const items = ref<LostAndFoundItem[]>([])
const managed = ref<ManagedMember[]>([])
const managedIds = computed(() => managed.value.map(m => m.id))
const {srcFor, load: loadImage, revokeAll} = useAuthImages<number>()

const showCreate = ref(false)
/**
 * The entry the open dialog already filed. A report is two requests, and the picture is the second:
 * remembering the entry is what makes a second press attach the picture to it rather than file the
 * whole thing again.
 */
const pendingItemId = ref<number | null>(null)
const imageTargetId = ref<number | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)

/**
 * What went wrong, in the server's own words where it said anything.
 *
 * <p>Where it said nothing, the described sentence stands rather than the general one: a dropped
 * connection and a request that timed out both say so, and only a failure nobody can name falls back
 * to admitting that.
 */
function failureText(e: unknown): string {
  if (e instanceof UnreadableImageError) return t('lostAndFound.imageUnreadable')
  return apiErrorMessage(e) ?? describeFailure(e, t).message
}

/**
 * A picture the browser could not read is the reader's own file and no fault of Ember's, so it is
 * stated as a fact and offers no bug report. It is recognised by the sentence {@link failureText}
 * already put there, the same way the alert recognises the general one.
 */
function shown(described: Failure | null): Failure | null {
  if (!described) return null
  if (described.message !== t('lostAndFound.imageUnreadable')) return described
  return {...described, guidance: t('lostAndFound.imageUnreadableGuidance'), reportable: false}
}

const {loading, failure, reload} = useAsyncLoader(async () => {
  revokeAll()
  items.value = await lostAndFound.listItems()
  await Promise.all(items.value.filter(i => i.hasImage)
      .map(i => loadImage(i.id, `/lost-and-found/${i.id}/image`)))
})

if (hasPermission(StationPermission.MEMBER_GUARDIAN)) {
  managedMembersApi.listManaged().then(found => (managed.value = found)).catch(() => (managed.value = []))
}

const {running: creating, failure: rawCreateFailure, run: createItem} = useAsyncAction(
    async (payload: LostItemCreatePayload) => {
      if (pendingItemId.value === null) {
        const item = await lostAndFound.createItem({description: payload.description, foundAt: payload.foundAt})
        pendingItemId.value = item.id
        await reload()
      }
      if (payload.imageFile) {
        await lostAndFound.uploadImage(pendingItemId.value, payload.imageFile)
        await reload()
      }
      closeCreate()
    },
    {formatError: failureText},
)

function closeCreate() {
  showCreate.value = false
  pendingItemId.value = null
}

function openCreate() {
  pendingItemId.value = null
  showCreate.value = true
}

const showClaim = ref(false)
const claimTarget = ref<number | null>(null)

function requestClaim(itemId: number) {
  claimTarget.value = itemId
  showClaim.value = true
}

const {running: claiming, failure: claimFailure, run: confirmClaim} = useAsyncAction(
    async (forMemberId: number | null) => {
      if (claimTarget.value === null) return
      await lostAndFound.claimItem(claimTarget.value, {memberId: forMemberId})
      showClaim.value = false
      claimTarget.value = null
      await reload()
      refreshSidebarCounts()
    },
    {formatError: failureText},
)

const release = useConfirmAction<number>({
  onConfirm: (itemId) => lostAndFound.releaseItem(itemId).then(() => undefined),
  onSuccess: async () => {
    await reload()
    refreshSidebarCounts()
  },
})

const provided = useConfirmAction<number>({
  onConfirm: (itemId) => lostAndFound.markProvided(itemId),
  onSuccess: async () => {
    await reload()
    refreshSidebarCounts()
  },
})

const {running: confirming, run: runConfirm} = useAsyncAction(
    (confirm: () => Promise<void>) => confirm(),
)

const {failure: deleteFailure, run: handleDelete} = useAsyncAction(
    async (itemId: number) => {
      await lostAndFound.deleteItem(itemId)
      await reload()
      refreshSidebarCounts()
    },
    {formatError: failureText},
)

const {failure: addImageFailure, run: uploadFor} = useAsyncAction(
    async (itemId: number, file: File) => {
      await lostAndFound.uploadImage(itemId, file)
      await reload()
    },
    {formatError: failureText},
)

function pickImageFor(itemId: number) {
  imageTargetId.value = itemId
  imageInputRef.value?.click()
}

function imagePicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file && imageTargetId.value !== null) uploadFor(imageTargetId.value, file)
}

const displayFailure = computed(() => shown(claimFailure.value
    ?? deleteFailure.value
    ?? addImageFailure.value
    ?? release.failure.value
    ?? provided.failure.value
    ?? failure.value))

const createFailure = computed(() => shown(rawCreateFailure.value))
</script>

<template>
  <ViewContent
      :title="t('pages.lost-and-found.title')"
      :subtitle="t('pages.lost-and-found.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <PrimaryButton :icon="['fas', 'plus']" v-if="canCreate()" @click="openCreate">
          {{ t('lostAndFound.create') }}
        </PrimaryButton>
      </div>

      <FailureAlert :failure="displayFailure"/>

      <AsyncSection
          :empty="items.length === 0"
          :empty-message="t('lostAndFound.empty')"
          :loading="loading"
      >
        <div class="grid grid-cols-1 sm:grid-cols-[repeat(auto-fill,minmax(18rem,1fr))] gap-4">
          <LostItemCard v-for="item in items" :key="item.id" :item="item"
                        :image-src="srcFor(item.id) ?? undefined"
                        :my-member-id="myMemberId()" :managed-member-ids="managedIds"
                        :can-manage="canManage()" :can-add-image="canCreate()"
                        @claim="requestClaim" @release="release.request" @provided="provided.request"
                        @delete="handleDelete" @add-image="pickImageFor"/>
        </div>
      </AsyncSection>

      <input ref="imageInputRef" type="file" accept="image/*" class="hidden" @change="imagePicked"/>

      <LostItemClaimModal v-model="showClaim" :managed="managed" :loading="claiming"
                          @confirm="confirmClaim"/>

      <LostItemConfirmModal v-model="release.show.value" :title="t('lostAndFound.releaseConfirmTitle')"
                            :message="t('lostAndFound.releaseConfirmMessage')"
                            :confirm-label="t('lostAndFound.release')"
                            :loading="confirming" @confirm="runConfirm(release.confirm)"/>

      <LostItemConfirmModal v-model="provided.show.value" :title="t('lostAndFound.providedConfirmTitle')"
                            :message="t('lostAndFound.providedConfirmMessage')"
                            :confirm-label="t('lostAndFound.provided')"
                            :loading="confirming" @confirm="runConfirm(provided.confirm)"/>

      <LostItemCreateModal v-model="showCreate" :creating="creating" :failure="createFailure"
                           :saved-without-image="pendingItemId !== null"
                           @submit="createItem" @close="closeCreate"/>
    </div>
  </ViewContent>
</template>
