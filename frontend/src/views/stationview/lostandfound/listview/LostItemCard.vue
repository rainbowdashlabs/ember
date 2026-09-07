/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type {LostAndFoundItem} from '@/api/lostAndFound'
import {formatDate} from '@/util/format'

const props = defineProps<{
  item: LostAndFoundItem
  imageSrc?: string
  myMemberId?: number
  /** The members in the reader's care, so a claim made for one of them still reads as theirs. */
  managedMemberIds?: number[]
  canManage: boolean
  canAddImage: boolean
}>()

const emit = defineEmits<{
  (e: 'claim', itemId: number): void
  (e: 'release', itemId: number): void
  (e: 'provided', itemId: number): void
  (e: 'delete', itemId: number): void
  (e: 'addImage', itemId: number): void
}>()

const {t} = useI18n()

/**
 * Whether the reader may take this claim back: their own, one they made for somebody in their
 * care, or anybody's if they look after the lost and found.
 */
const canRelease = computed(() => props.canManage
    || props.item.claimedBy === props.myMemberId
    || (props.managedMemberIds ?? []).includes(props.item.claimedBy ?? -1))
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="lost-item-card"
                    :class="{'opacity-60': item.claimedBy && !canManage}">
    <img v-if="imageSrc"
         :src="imageSrc"
         :alt="item.description ?? t('lostAndFound.noDescription')"
         class="w-full h-48 object-cover rounded-lg"/>
    <div v-else class="w-full h-48 bg-(--bg-accent) rounded-lg flex flex-col items-center justify-center gap-2"
         data-testid="item-placeholder">
      <font-awesome-icon :icon="item.hasImage ? ['fas', 'triangle-exclamation'] : ['fas', 'box-open']"
                         class="text-4xl text-(--text-muted)"/>
      <p v-if="item.hasImage" class="text-xs text-(--text-muted)">{{ t('lostAndFound.imageUnavailable') }}</p>
    </div>

    <div>
      <p class="text-sm font-medium">{{ item.description || t('lostAndFound.noDescription') }}</p>
      <p class="text-xs text-(--text-muted)">{{ t('lostAndFound.foundAt') }}: {{ formatDate(item.foundAt) || '–' }}</p>
    </div>

    <div v-if="item.claimedBy" class="space-y-2">
      <SuccessBadge v-if="item.claimedBy === myMemberId">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
        {{ t('lostAndFound.claimedByYou') }}
      </SuccessBadge>
      <SuccessBadge v-else>
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
        {{ t('lostAndFound.claimedBy', {name: item.claimedByName ?? '?'}) }}
      </SuccessBadge>

      <div class="flex items-center gap-2">
        <SuccessButton v-if="canManage" :icon="['fas', 'circle-check']" class="flex-1 text-sm"
                       @click="emit('provided', item.id)">
          {{ t('lostAndFound.provided') }}
        </SuccessButton>
        <SecondaryButton v-if="canRelease" :icon="['fas', 'rotate-left']" class="flex-1 text-sm"
                         @click="emit('release', item.id)">
          {{ t('lostAndFound.release') }}
        </SecondaryButton>
        <DeleteButton v-if="canManage" @click="emit('delete', item.id)"/>
      </div>
    </div>

    <div v-else class="flex items-center gap-2">
      <SuccessButton class="flex-1 text-sm" @click="emit('claim', item.id)">
        {{ t('lostAndFound.claim') }}
      </SuccessButton>
      <DeleteButton v-if="canManage" @click="emit('delete', item.id)"/>
    </div>

    <SecondaryButton v-if="canAddImage && !item.hasImage" :icon="['fas', 'camera']" class="w-full text-sm"
                     @click="emit('addImage', item.id)">
      {{ t('lostAndFound.addImage') }}
    </SecondaryButton>
  </NeutralContainer>
</template>
