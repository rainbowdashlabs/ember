/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DragList from '@/components/input/DragList.vue'
import CollectionLineRow from './CollectionLineRow.vue'
import CollectionWindowFields from './CollectionWindowFields.vue'
import type {ResolvedCollection, ResolvedCollectionLine} from '@/api/inventoryCollections'

const dateFrom = defineModel<string>('dateFrom', {required: true})
const dateTo = defineModel<string>('dateTo', {required: true})

defineProps<{
  resolved: ResolvedCollection
  editable: boolean
}>()

const emit = defineEmits<{
  addLine: []
  rename: []
  remove: []
  updateQuantity: [line: ResolvedCollectionLine, quantity: number]
  removeLine: [line: ResolvedCollectionLine]
  reorder: [fromIndex: number, toIndex: number]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-start justify-between gap-2">
      <div class="min-w-0">
        <SubHeader>{{ resolved.collection.name }}</SubHeader>
        <MutedText v-if="resolved.collection.note">{{ resolved.collection.note }}</MutedText>
      </div>
      <div v-if="editable" class="flex items-center gap-1 shrink-0">
        <EditButton data-testid="collection-rename" @click="emit('rename')"/>
        <DeleteButton data-testid="collection-delete" @click="emit('remove')"/>
      </div>
    </div>

    <CollectionWindowFields v-model:date-from="dateFrom" v-model:date-to="dateTo"/>

    <FieldHint v-if="resolved.holdsClusterOwned" data-testid="collection-cluster-hint">
      {{ t('inventory.collections.clusterHint') }}
    </FieldHint>

    <EmptyState v-if="resolved.lines.length === 0">{{ t('inventory.collections.noLines') }}</EmptyState>

    <DragList
        v-else
        :items="resolved.lines"
        :key-fn="(line) => line.lineId"
        :disabled="!editable"
        @reorder="(from, to) => emit('reorder', from, to)"
    >
      <template #default="{item}">
        <CollectionLineRow
            :line="item"
            :editable="editable"
            @update-quantity="(line, quantity) => emit('updateQuantity', line, quantity)"
            @remove="(line) => emit('removeLine', line)"
        />
      </template>
    </DragList>

    <SecondaryButton v-if="editable" :icon="['fas', 'plus']" data-testid="collection-add-line" @click="emit('addLine')">
      {{ t('inventory.collections.addLine') }}
    </SecondaryButton>
  </NeutralContainer>
</template>
