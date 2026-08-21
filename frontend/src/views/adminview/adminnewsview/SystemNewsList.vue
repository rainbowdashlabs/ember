/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {StationUserTypeLabels, type StationUserTypeName} from '@/api/types'
import type {SystemNewsEntry} from '@/api/adminNews'
import {formatDateTime} from '@/util/format'

defineProps<{
  entries: SystemNewsEntry[]
}>()

const emit = defineEmits<{
  (e: 'edit', entry: SystemNewsEntry): void
  (e: 'retract', entry: SystemNewsEntry): void
}>()

const {t} = useI18n()

function label(userType: string): string {
  return StationUserTypeLabels[userType as StationUserTypeName] ?? userType
}
</script>

<template>
  <div class="space-y-3">
    <NeutralContainer v-for="entry in entries" :key="entry.id" class="space-y-2" data-testid="system-news">
      <div class="flex items-start justify-between gap-3">
        <div class="space-y-1">
          <SubHeader class="flex flex-wrap items-center gap-2">
            <span>{{ entry.title }}</span>
            <SecondaryBadge v-if="!entry.publishedAt">{{ t('adminNews.draft') }}</SecondaryBadge>
          </SubHeader>
          <p class="text-xs text-(--text-muted)">
            {{ entry.publishedAt ? formatDateTime(entry.publishedAt) : formatDateTime(entry.createdAt) }}
            &middot; {{ t('adminNews.commentCount', {count: entry.commentCount}) }}
          </p>
          <!-- No user types at all means the entry is for everyone, which is worth saying rather
               than leaving as an empty space the reader has to interpret. -->
          <p class="text-xs text-(--text-muted)">
            <template v-if="entry.userTypes.length === 0">{{ t('adminNews.forEveryone') }}</template>
            <template v-else>
              <InfoBadge v-for="userType in entry.userTypes" :key="userType" class="mr-1">
                {{ label(userType) }}
              </InfoBadge>
            </template>
          </p>
        </div>
        <div class="flex shrink-0 items-center gap-1">
          <EditButton @click="emit('edit', entry)"/>
          <DeleteButton @click="emit('retract', entry)"/>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>
