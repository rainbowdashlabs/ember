/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {ClusterField} from '@/api/clusterFields'

defineProps<{
  field: ClusterField
  editable: boolean
  busy: boolean
}>()

const emit = defineEmits<{
  edit: [field: ClusterField]
  remove: [fieldId: number]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex flex-wrap items-center justify-between gap-3">
    <div class="min-w-0">
      <p class="font-medium truncate">
        {{ field.name }}
        <SecondaryBadge v-if="field.stationReadonly" class="ml-2">
          {{ t('clusterFields.readonlyBadge') }}
        </SecondaryBadge>
      </p>
      <p class="text-sm text-(--text-muted)">
        {{ t(`clusterFields.types.${field.fieldType}`) }} · {{ t(`clusterFields.scopes.${field.scope}`) }}
      </p>
    </div>
    <div v-if="editable" class="flex items-center gap-2">
      <EditButton :disabled="busy" @click="emit('edit', field)"/>
      <DeleteButton :disabled="busy" @click="emit('remove', field.id)"/>
    </div>
  </NeutralContainer>
</template>
