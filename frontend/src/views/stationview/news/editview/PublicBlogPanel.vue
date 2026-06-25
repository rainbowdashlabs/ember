/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const publicBlog = defineModel<boolean>('publicBlog', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <div>
        <SubHeader>{{ t('news.publicBlog') }}</SubHeader>
        <p class="text-xs text-(--text-muted)">{{ t('news.publicBlogHint') }}</p>
      </div>
      <ToggleInput v-model="publicBlog"/>
    </div>
  </NeutralContainer>
</template>
