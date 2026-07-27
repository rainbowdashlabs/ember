/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type {LostAndFoundItem} from '@/api/types'
import {formatDate} from '@/util/format'

const props = defineProps<{
  item: LostAndFoundItem
  imageSrc?: string
  myMemberId?: number
  canManage: boolean
}>()

const emit = defineEmits<{
  (e: 'claim', itemId: number): void
  (e: 'provided', itemId: number): void
  (e: 'delete', itemId: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" :class="{'opacity-60': item.claimedBy && !canManage}">
    <img v-if="imageSrc"
         :src="imageSrc"
         :alt="item.description ?? t('lostAndFound.noDescription')"
         class="w-full h-48 object-cover rounded-lg"/>
    <div v-else-if="!item.hasImage" class="w-full h-48 bg-(--bg-accent) rounded-lg flex items-center justify-center">
      <font-awesome-icon :icon="['fas', 'box-open']" class="text-4xl text-(--text-muted)"/>
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

      <div v-if="canManage" class="flex items-center gap-2">
        <SuccessButton :icon="['fas', 'circle-check']" class="flex-1 text-sm" @click="emit('provided', item.id)">
          {{ t('lostAndFound.provided') }}
        </SuccessButton>
        <DeleteButton @click="emit('delete', item.id)"/>
      </div>
    </div>

    <div v-else class="flex items-center gap-2">
      <SuccessButton class="flex-1 text-sm" @click="emit('claim', item.id)">
        {{ t('lostAndFound.claim') }}
      </SuccessButton>
      <DeleteButton v-if="canManage" @click="emit('delete', item.id)"/>
    </div>
  </NeutralContainer>
</template>
