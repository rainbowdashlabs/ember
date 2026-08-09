/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {InventoryItem, InventorySize} from '@/api/inventory'
import type {MemberIdentity} from '@/api/types'
import type {ItemLocationResponse} from '@/api/inventoryContainers'
import {formatDate} from '@/util/format'

const props = defineProps<{
  item: InventoryItem
  sizes: InventorySize[]
  location: ItemLocationResponse | null
  assignedMemberIdentity: MemberIdentity | null
}>()

const {t} = useI18n()

function sizeLabel(sizeId?: number | null): string {
  if (sizeId == null) return '—'
  return props.sizes.find(s => s.id === sizeId)?.label ?? String(sizeId)
}
</script>

<template>
  <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
    <div v-if="props.item.internalId">
      <FieldLabel class="text-xs">{{ t('itemDetail.internalId') }}</FieldLabel>
      <span class="font-mono">{{ props.item.internalId }}</span>
    </div>
    <div v-if="props.sizes.length > 0">
      <FieldLabel class="text-xs">{{ t('itemDetail.size') }}</FieldLabel>
      <span>{{ sizeLabel(props.item.sizeId) }}</span>
    </div>
    <div>
      <FieldLabel class="text-xs">{{ t('itemDetail.status') }}</FieldLabel>
      <ErrorBadge v-if="props.item.lostAt">{{ t('profile.lostSince') }} {{ formatDate(props.item.lostAt) }}</ErrorBadge>
      <SuccessBadge v-else-if="props.item.assignedTo">{{ t('itemDetail.statusAssigned') }}</SuccessBadge>
      <InfoBadge v-else>{{ t('itemDetail.available') }}</InfoBadge>
    </div>
    <div v-if="props.assignedMemberIdentity">
      <FieldLabel class="text-xs">{{ t('itemDetail.assignedTo') }}</FieldLabel>
      <MemberName :identity="props.assignedMemberIdentity"/>
    </div>
    <div v-if="props.location && props.location.pathSegments.length > 0" class="col-span-2 sm:col-span-4">
      <FieldLabel class="text-xs">{{ t('itemDetail.location') }}</FieldLabel>
      <span class="text-sm">
        <template v-for="(seg, i) in props.location.pathSegments" :key="i">
          <router-link
              :to="{name: 'inventory-container-detail', params: {id: String(props.location.pathIds[i])}}"
              class="hover:underline"
          >
            {{ seg }}
          </router-link>
          <span v-if="i < props.location.pathSegments.length - 1" class="text-(--text-muted)"> / </span>
        </template>
      </span>
    </div>
  </div>
</template>
