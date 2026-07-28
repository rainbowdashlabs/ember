/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import type {InventoryItem} from '@/api/inventory'

const props = defineProps<{
  item: InventoryItem
}>()

const emit = defineEmits<{
  assign: []
  unassign: []
  markLost: []
  markFound: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('itemDetail.actions') }}</SubHeader>
    <div class="flex flex-wrap gap-2">
      <PrimaryButton :icon="['fas', 'user-plus']" @click="emit('assign')">
        {{ t('itemDetail.assign') }}
      </PrimaryButton>
      <SecondaryButton v-if="props.item.assignedTo" :icon="['fas', 'user-minus']" @click="emit('unassign')">
        {{ t('itemDetail.unassign') }}
      </SecondaryButton>
      <ErrorButton v-if="!props.item.lostAt" :icon="['fas', 'triangle-exclamation']" @click="emit('markLost')">
        {{ t('itemDetail.markLost') }}
      </ErrorButton>
      <SuccessButton v-if="props.item.lostAt" :icon="['fas', 'check']" @click="emit('markFound')">
        {{ t('itemDetail.markFound') }}
      </SuccessButton>
    </div>
  </NeutralContainer>
</template>
