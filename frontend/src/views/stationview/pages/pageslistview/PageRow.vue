/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {StationPage} from '@/api/pageManage'

const props = defineProps<{
  page: StationPage
  depth: number
  canEdit: boolean
  canManage: boolean
  landingPageId: number | null
}>()

const emit = defineEmits<{
  (e: 'edit', page: StationPage): void
  (e: 'duplicate', page: StationPage): void
  (e: 'toggle-publish', page: StationPage): void
  (e: 'set-landing', page: StationPage): void
  (e: 'delete', page: StationPage): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer
      data-testid="page-row"
      class="flex items-center gap-3 my-1"
      :style="{marginLeft: `${props.depth * 1.5}rem`}"
  >
    <div class="flex-1 min-w-0 flex items-center gap-2 flex-wrap">
      <span class="font-medium truncate">{{ props.page.title }}</span>
      <span class="text-xs text-[var(--text-muted)]">/{{ props.page.slug }}</span>
      <SuccessBadge v-if="props.page.published">{{ t('stationPages.published') }}</SuccessBadge>
      <SecondaryBadge v-else>{{ t('stationPages.draft') }}</SecondaryBadge>
      <font-awesome-icon
          v-if="props.landingPageId === props.page.id"
          :icon="['fas', 'star']"
          class="h-4 w-4 text-info-accent"
          :title="t('stationPages.landingPage')"
      />
    </div>

    <div class="flex items-center gap-1 shrink-0">
      <EditButton v-if="props.canEdit" @click="emit('edit', props.page)"/>
      <IconButton
          v-if="props.canEdit"
          :icon="['fas', 'clone']"
          :label="t('stationPages.duplicate')"
          class="text-[var(--text-muted)] hover:text-[var(--text)]"
          @click="emit('duplicate', props.page)"
      />
      <IconButton
          v-if="props.canManage"
          :icon="['fas', props.page.published ? 'eye-slash' : 'eye']"
          :label="props.page.published ? t('stationPages.unpublish') : t('stationPages.publish')"
          class="text-[var(--text-muted)] hover:text-[var(--text)]"
          @click="emit('toggle-publish', props.page)"
      />
      <IconButton
          v-if="props.canManage"
          :icon="['fas', 'star']"
          :label="t('stationPages.setLandingPage')"
          :disabled="((!props.page.published || props.page.parentId != null) && props.landingPageId !== props.page.id)"
          :class="props.landingPageId === props.page.id ? 'text-info-accent' : 'text-[var(--text-muted)] hover:text-info-accent'"
          @click="emit('set-landing', props.page)"
      />
      <DeleteButton v-if="props.canManage" @click="emit('delete', props.page)"/>
    </div>
  </NeutralContainer>
</template>
