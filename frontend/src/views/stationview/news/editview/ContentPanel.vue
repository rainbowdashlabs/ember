/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const title = defineModel<string>('title', {required: true})
const contentMarkdown = defineModel<string>('contentMarkdown', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('news.titleField') }}</FieldLabel>
      <TextInput v-model="title" :placeholder="t('news.titlePlaceholder')"/>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('news.content') }}</FieldLabel>
      <MarkdownEditor v-model="contentMarkdown" :placeholder="t('news.contentPlaceholder')"/>
    </div>
  </NeutralContainer>
</template>
