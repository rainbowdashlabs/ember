/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {UserTag} from '@/api/types'

const {t} = useI18n()

defineProps<{
  tags: UserTag[]
  selectedTag: UserTag | null
  canConvertToGroup: boolean
}>()

const emit = defineEmits<{
  (e: 'create'): void
  (e: 'select', tag: UserTag): void
  (e: 'edit', tag: UserTag): void
  (e: 'delete', tag: UserTag): void
  (e: 'convert', tag: UserTag): void
}>()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('userTags.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']" @click="emit('create')">
        {{ t('userTags.create') }}
      </PrimaryButton>
    </div>

    <EmptyState v-if="tags.length === 0">{{ t('userTags.empty') }}</EmptyState>

    <div class="space-y-2">
      <NeutralContainer
          v-for="tag in tags"
          :key="tag.id"
          :class="selectedTag?.id === tag.id ? 'border-primary' : 'hover:border-primary'"
          class="flex items-center justify-between gap-2 flex-wrap cursor-pointer transition-colors"
          @click="emit('select', tag)"
      >
        <span class="flex items-center gap-2">
          <span v-if="tag.color" class="inline-block h-3 w-3 rounded-full" :style="{ backgroundColor: tag.color }"></span>
          <span class="font-medium">{{ tag.name }}</span>
          <font-awesome-icon v-if="tag.visible" :icon="['fas', 'eye']" class="text-xs text-(--text-muted)"/>
        </span>
        <div class="flex items-center gap-2">
          <MutedIconButton v-if="canConvertToGroup" :icon="['fas', 'people-group']" :label="t('userTags.convertToGroup')" @click.stop="emit('convert', tag)"/>
          <EditButton @click.stop="emit('edit', tag)"/>
          <DeleteButton @click.stop="emit('delete', tag)"/>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
