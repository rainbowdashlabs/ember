/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import CommentThread from '@/components/comment/CommentThread.vue'
import type {CommentResponse} from '@/api/generated/schema'

const {t} = useI18n()

const comments = computed<CommentResponse[]>(() => [
  {
    id: 1,
    author: {stationUid: 's1', memberUid: 'u2', name: 'Lisa Schmidt', nameColor: null, stationName: null, displayTag: null},
    content: t('helpCenter.ticketDetail.commentExample1'),
    deleted: false,
    createdAt: '2026-06-05T12:30:00Z',
  },
  {
    id: 2,
    parentId: 1,
    author: {stationUid: 's1', memberUid: 'u1', name: 'Max Mustermann', nameColor: null, stationName: null, displayTag: null},
    content: t('helpCenter.ticketDetail.commentReply1'),
    deleted: false,
    createdAt: '2026-06-05T13:10:00Z',
    updatedAt: '2026-06-05T13:20:00Z',
  },
])
</script>

<template>
  <CommentThread :comments="comments" :members="[]" readonly/>
</template>
