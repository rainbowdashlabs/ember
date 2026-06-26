/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const props = defineProps<{
  title: string
  diff?: string | null
  html?: string | null
  addedKeyPrefix: string
  removedKeyPrefix: string
}>()

const {t} = useI18n()
const showFull = ref(false)

function parseDiff(diff: string | undefined | null): { added: string[]; removed: string[] } {
  if (!diff) return {added: [], removed: []}
  const lines = diff.split('\n')
  const added: string[] = []
  const removed: string[] = []
  for (const line of lines) {
    if (line.startsWith('+ ') && line.trim() !== '+') {
      const content = line.substring(2).trim()
      if (content) added.push(content)
    } else if (line.startsWith('- ') && line.trim() !== '-') {
      const content = line.substring(2).trim()
      if (content) removed.push(content)
    }
  }
  return {added, removed}
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ props.title }}</SubHeader>

    <template v-if="props.diff">
      <div class="text-xs text-(--text-muted) mb-1">{{ t('reconsent.whatChanged') }}</div>
      <div class="border border-(--border) rounded-lg p-3 text-sm space-y-1 max-h-48 overflow-y-auto bg-(--bg)">
        <div v-for="(line, i) in parseDiff(props.diff).removed" :key="props.removedKeyPrefix + i"
             class="text-error line-through">{{ line }}</div>
        <div v-for="(line, i) in parseDiff(props.diff).added" :key="props.addedKeyPrefix + i"
             class="text-success">{{ line }}</div>
      </div>
    </template>

    <LinkButton @click="showFull = !showFull">
      {{ showFull ? t('reconsent.hideFullText') : t('reconsent.showFullText') }}
    </LinkButton>
    <div v-if="showFull && props.html"
         class="legal-content max-h-96 overflow-y-auto border border-(--border) rounded-lg p-3"
         v-html="props.html"/>
  </NeutralContainer>
</template>
