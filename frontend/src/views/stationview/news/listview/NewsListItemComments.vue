/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import CommentSection from '@/components/comment/CommentSection.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import {newsCommentSource, partnerNewsCommentSource} from '@/api/news'

const props = defineProps<{
  newsId: number
  /** The partner station sharing the entry, or nothing for one of the station's own. */
  stationUid?: string
  commentCount: number
  open: boolean
}>()

const emit = defineEmits<{toggle: []}>()

const {t} = useI18n()

const commentSource = computed(() => (props.stationUid
  ? partnerNewsCommentSource(props.stationUid, props.newsId)
  : newsCommentSource(props.newsId)))
</script>

<template>
  <div class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
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
      <CommentSection :source="commentSource"/>
    </div>
  </div>
</template>
