/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {Station} from '@/api/stations'

defineProps<{
  stations: Station[]
}>()

defineEmits<{
  create: []
  import: []
  edit: [id: string]
  delete: [station: Station]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <PrimaryContainer
        class="flex flex-col items-center justify-center gap-2 cursor-pointer py-6 border-dashed hover:opacity-80 transition-opacity"
        @click="$emit('create')"
    >
      <font-awesome-icon :icon="['fas', 'plus']" class="text-2xl"/>
      <span class="font-medium">{{ t('adminStations.create') }}</span>
    </PrimaryContainer>

    <NeutralContainer
        class="flex flex-col items-center justify-center gap-2 cursor-pointer py-6 hover:opacity-80 transition-opacity"
        @click="$emit('import')"
    >
      <font-awesome-icon :icon="['fas', 'upload']" class="text-2xl text-(--text-muted)"/>
      <span class="font-medium">{{ t('adminStations.importStation') }}</span>
    </NeutralContainer>

    <NeutralContainer v-for="station in stations" :key="station.id"
                      class="flex items-center justify-between py-6">
      <span class="font-medium text-lg">{{ station.name }}</span>
      <div class="flex items-center gap-2">
        <EditButton @click="$emit('edit', station.id.toString())"/>
        <DeleteButton @click="$emit('delete', station)"/>
      </div>
    </NeutralContainer>
  </div>
</template>
