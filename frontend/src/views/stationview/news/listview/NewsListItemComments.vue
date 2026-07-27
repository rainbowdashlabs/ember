/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NewsCommentSection from '@/components/comment/NewsCommentSection.vue'
import LinkButton from '@/components/button/LinkButton.vue'

defineProps<{
  newsId: number
  stationUid?: string
  commentCount: number
  open: boolean
}>()

const emit = defineEmits<{toggle: []}>()

const {t} = useI18n()
</script>

<template>
  <div class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent" @click.stop>
    <LinkButton
      class="!text-sm !text-(--text-muted) hover:!text-primary hover:!no-underline flex items-center gap-1.5"
      @click="emit('toggle')"
    >
      <font-awesome-icon :icon="['fas', 'comment']" class="h-3.5 w-3.5"/>
      <template v-if="commentCount > 0">
        {{ t('news.commentsCount', {count: commentCount}) }}
      </template>
      <template v-else>
        {{ t('news.addComment') }}
      </template>
      <font-awesome-icon
        :icon="['fas', open ? 'chevron-up' : 'chevron-down']"
        class="h-2.5 w-2.5 ml-0.5"
      />
    </LinkButton>
    <div v-if="open" class="mt-3">
      <NewsCommentSection :news-id="newsId" :station-uid="stationUid"/>
    </div>
  </div>
</template>
