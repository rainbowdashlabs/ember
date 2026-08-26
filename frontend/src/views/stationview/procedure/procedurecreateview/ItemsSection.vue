/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DragList from '@/components/input/DragList.vue'
import ProcedureItemCard from '@/views/stationview/procedure/procedurecreateview/ProcedureItemCard.vue'
import type {EditableItem} from '@/views/stationview/procedure/procedurecreateview/types'

const {t} = useI18n()

defineProps<{
  items: EditableItem[]
}>()

const emit = defineEmits<{
  add: []
  reorder: [fromIndex: number, toIndex: number]
  remove: [index: number]
}>()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('procedures.items') }}</SubHeader>
      <SecondaryButton @click="emit('add')">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/> {{ t('procedures.addItem') }}
      </SecondaryButton>
    </div>

    <MutedText v-if="items.length === 0" size="sm">{{ t('procedures.noItems') }}</MutedText>

    <DragList
        :items="items"
        :key-fn="(item) => item.tempId"
        class="space-y-2"
        @reorder="(from, to) => emit('reorder', from, to)"
    >
      <template #default="{item, index}">
        <ProcedureItemCard :item="item" :all-items="items" @remove="emit('remove', index)"/>
      </template>
    </DragList>
  </NeutralContainer>
</template>
