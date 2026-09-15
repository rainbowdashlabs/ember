/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import {renderMarkdown} from '@/util/markdown'
import client from '@/api/client'
import {getChangelog, type ChangelogEntry} from '@/api/system'

/**
 * What every version of this instance brought.
 *
 * <p>Read from the instance, not from GitHub out of the reader's browser: an installation with no
 * way out can still say what it changed, and nobody has to leave an address somewhere else to read
 * what their own instance does. The version being run is marked, because the question behind the
 * page is usually "what changed for us", not "what has been released".
 */
const {t} = useI18n()

const entries = ref<ChangelogEntry[]>([])
const currentVersion = ref('')
const loading = ref(true)
const error = ref('')

/** The numbers alone, which is what a version heading carries; a build off a branch says more. */
function versionNumber(version: string): string {
    return version.trim().split(' ')[0]?.replace(/^v/, '') ?? ''
}

onMounted(async () => {
    try {
        const [changelog, config] = await Promise.all([
            getChangelog(),
            client.get<{version?: string}>('/public/config').then(res => res.data).catch(() => ({version: ''})),
        ])
        entries.value = changelog
        currentVersion.value = versionNumber(config.version ?? '')
    } catch {
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
      <router-link to="/?home" class="text-sm text-[var(--link)] hover:underline">{{ t('common.back') }}</router-link>
    </div>

    <Spinner v-if="loading" size="lg"/>
    <FailureAlert :message="error"/>

    <p v-if="!loading && entries.length === 0 && !error" class="text-[var(--text-muted)] text-center py-8">
      {{ t('patchNotes.noReleases') }}
    </p>

    <NeutralContainer
        v-for="entry in entries"
        :key="entry.version"
        :data-version="entry.version"
        class="space-y-3"
        data-testid="changelog-version"
    >
      <div class="flex flex-wrap items-center gap-2">
        <SectionHeader class="text-lg font-bold">{{ entry.version }}</SectionHeader>
        <PrimaryBadge v-if="entry.version === currentVersion" data-testid="changelog-current">
          {{ t('patchNotes.installed') }}
        </PrimaryBadge>
      </div>

      <div class="markdown-content text-sm" v-html="renderMarkdown(entry.body)"/>
    </NeutralContainer>
  </div>
</template>
