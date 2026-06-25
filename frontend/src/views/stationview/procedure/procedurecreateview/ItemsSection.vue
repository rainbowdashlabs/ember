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
import ProcedureItemCard from '@/views/stationview/procedure/procedurecreateview/ProcedureItemCard.vue'
import type {EditableItem} from '@/views/stationview/procedure/procedurecreateview/types'

const {t} = useI18n()

defineProps<{
  items: EditableItem[]
}>()

const emit = defineEmits<{
  add: []
  move: [index: number, direction: -1 | 1]
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

    <ProcedureItemCard
        v-for="(item, index) in items" :key="item.tempId"
        :item="item" :index="index" :total-items="items.length" :all-items="items"
        @move="emit('move', index, $event)" @remove="emit('remove', index)"
    />
  </NeutralContainer>
</template>
