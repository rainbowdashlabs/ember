/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import CommentThread from '@/components/comment/CommentThread.vue'
import type {Comment} from '@/api/types'
import type {MemberCompletion} from '@/api/stationMembers'

defineOptions({inheritAttrs: false})

const props = defineProps<{
  comments: Comment[]
  members: MemberCompletion[]
  loading: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('comments.title') }}</SubHeader>
    <Spinner v-if="props.loading" size="sm"/>
    <template v-if="!props.loading">
      <CommentThread
          v-bind="$attrs"
          :comments="props.comments"
          :members="props.members"
      />
    </template>
  </NeutralContainer>
</template>
