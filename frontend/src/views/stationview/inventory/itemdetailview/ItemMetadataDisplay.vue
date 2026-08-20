/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ItemFact from './ItemFact.vue'
import {ItemOwner, type InventoryItem, type InventorySize} from '@/api/inventory'
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
  if (sizeId == null) return '-'
  return props.sizes.find(s => s.id === sizeId)?.label ?? String(sizeId)
}

/** Who has the item right now, which is a different question from who owns it. */
const custodyLabel = computed(() =>
    props.item.custody ? t(`itemDetail.custodyValues.${props.item.custody}`) : '-')

/**
 * Names the owner, and for gear the station does not own says whether that owner is reachable
 * in Ember or only stands behind the station's own record of it.
 */
const ownerLabel = computed(() => {
  if (props.item.ownerKind !== ItemOwner.CLUSTER) return t('itemDetail.ownerStation')
  return props.item.ownerClusterId != null ? t('itemDetail.ownerCluster') : t('itemDetail.ownerClusterOffline')
})
</script>

<template>
  <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
    <ItemFact v-if="props.item.internalId" :label="t('itemDetail.internalId')">
      <span class="font-mono">{{ props.item.internalId }}</span>
    </ItemFact>
    <ItemFact v-if="props.sizes.length > 0" :label="t('itemDetail.size')">
      <span>{{ sizeLabel(props.item.sizeId) }}</span>
    </ItemFact>
    <ItemFact :label="t('itemDetail.owner')">
      <span>{{ ownerLabel }}</span>
    </ItemFact>
    <ItemFact :label="t('itemDetail.custody')">
      <span>{{ custodyLabel }}</span>
    </ItemFact>
    <ItemFact :label="t('itemDetail.status')">
      <ErrorBadge v-if="props.item.lostAt">{{ t('profile.lostSince') }} {{ formatDate(props.item.lostAt) }}</ErrorBadge>
      <SuccessBadge v-else-if="props.item.assignedTo">{{ t('itemDetail.statusAssigned') }}</SuccessBadge>
      <InfoBadge v-else>{{ t('itemDetail.available') }}</InfoBadge>
    </ItemFact>
    <ItemFact v-if="props.assignedMemberIdentity" :label="t('itemDetail.assignedTo')">
      <MemberName :identity="props.assignedMemberIdentity"/>
    </ItemFact>
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
