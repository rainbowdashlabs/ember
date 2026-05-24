/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'

const { t } = useI18n()

interface GithubRelease {
  id: number
  tag_name: string
  name: string
  body: string
  published_at: string
  prerelease: boolean
  html_url: string
}

const releases = ref<GithubRelease[]>([])
const loading = ref(true)
const error = ref('')

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', { year: 'numeric', month: 'long', day: 'numeric' })
}

function renderMarkdown(body: string): string {
  // Simple markdown to HTML for release notes
  return body
    .replace(/^### (.+)$/gm, '<h3 class="font-semibold text-base mt-4 mb-1">$1</h3>')
    .replace(/^## (.+)$/gm, '<h2 class="font-semibold text-lg mt-5 mb-2">$1</h2>')
    .replace(/^# (.+)$/gm, '<h1 class="font-bold text-xl mt-6 mb-2">$1</h1>')
    .replace(/^\- \*\*(.+?)\*\*: (.+)$/gm, '<li><strong>$1</strong>: $2</li>')
    .replace(/^\- \*\*(.+?)\*\*(.*)$/gm, '<li><strong>$1</strong>$2</li>')
    .replace(/^\- (.+)$/gm, '<li>$1</li>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '<code class="text-xs bg-[var(--bg-accent)] px-1 py-0.5 rounded">$1</code>')
    .replace(/(<li>.*<\/li>\n?)+/g, (match) => `<ul class="list-disc pl-5 space-y-0.5 text-sm">${match}</ul>`)
    .replace(/\n\n/g, '<br/>')
}

onMounted(async () => {
  try {
    const res = await fetch('https://api.github.com/repos/rainbowdashlabs/ember/releases?per_page=20')
    if (!res.ok) throw new Error(`GitHub API error: ${res.status}`)
    releases.value = await res.json()
  } catch (e) {
    error.value = t('patchNotes.fetchError')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-8 space-y-6">
    <div class="flex items-center justify-between">
      <PageHeader>{{ t('patchNotes.title') }}</PageHeader>
      <router-link to="/" class="text-sm text-[var(--link)] hover:underline">{{ t('common.back') }}</router-link>
    </div>

    <Spinner v-if="loading" size="lg" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <p v-if="!loading && releases.length === 0 && !error" class="text-[var(--text-muted)] text-center py-8">
      {{ t('patchNotes.noReleases') }}
    </p>

    <NeutralContainer v-for="release in releases" :key="release.id" class="space-y-3">
      <div class="flex flex-wrap items-center gap-2">
        <SectionHeader class="text-lg font-bold">{{ release.name || release.tag_name }}</SectionHeader>
        <PrimaryBadge>{{ release.tag_name }}</PrimaryBadge>
        <SecondaryBadge v-if="release.prerelease">Pre-release</SecondaryBadge>
        <span class="text-xs text-[var(--text-muted)] ml-auto">{{ formatDate(release.published_at) }}</span>
      </div>

      <div v-if="release.body" class="markdown-content text-sm" v-html="renderMarkdown(release.body)" />

      <a :href="release.html_url" target="_blank" rel="noopener noreferrer" class="text-xs text-[var(--link)] hover:underline inline-flex items-center gap-1">
        <font-awesome-icon :icon="['fab', 'github']" class="w-3 h-3" />
        {{ t('patchNotes.viewOnGithub') }}
      </a>
    </NeutralContainer>
  </div>
</template>
