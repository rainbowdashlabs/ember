/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import EditButton from '@/components/button/EditButton.vue'
import type {ShareDetail} from '@/api/lending'

const props = defineProps<{
  detail: ShareDetail
  label: string
}>()

defineEmits<{ edit: [] }>()

const {t} = useI18n()

const levelLabel = computed(() => {
  if (props.detail.share.itemId != null) return t('lendingShare.levelItem')
  if (props.detail.share.artId != null) return t('lendingShare.levelArt')
  return t('lendingShare.levelInventory')
})
</script>

<template>
  <NeutralContainer>
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
      <div class="flex flex-col gap-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ label }}</span>
          <InfoBadge>{{ levelLabel }}</InfoBadge>
        </div>
        <div class="flex items-center gap-1 flex-wrap text-xs text-[var(--text-muted)]">
          <template v-if="detail.share.shareScope === 'ALL_PARTNERS'">
            {{ t('lendingShare.scopeValues.ALL_PARTNERS') }}
          </template>
          <template v-else-if="detail.partners.length === 0">
            {{ t('lendingShare.noPartnersNamed') }}
          </template>
          <StationBadge
              v-for="partner in detail.partners"
              :key="partner.partnerId"
              :station-name="partner.stationName"
          />
        </div>
      </div>
      <EditButton @click="$emit('edit')"/>
    </div>
  </NeutralContainer>
</template>
