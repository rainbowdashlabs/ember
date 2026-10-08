/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import type {SealedVersionResponse} from '@/api/generated/schema'
import {downloadAuthed} from '@/util/downloadAuthed'
import {formatDateTime, formatSize} from '@/util/format'

/**
 * What a sealed document says about its seal: that it is locked, and every sealed version it holds,
 * newest first, the one it serves marked current and the earlier ones with the date they were
 * superseded. Each version can be saved on its own where the door serves versions, so an earlier state
 * of the signatures stays at hand. The hash is shortened; it names the file, it is not meant to be read in
 * full.
 */
const props = withDefaults(defineProps<{
  versions: SealedVersionResponse[]
  /** The document's file name, which a saved version carries with its number. */
  fileName: string
  /** Where one version is served from, or null where this door serves none. */
  versionUrl?: ((version: number) => string) | null
}>(), {
  versionUrl: null,
})

const {t} = useI18n()

/** The first characters of a hash, enough to tell two versions apart at a glance. */
function shortHash(sha256: string): string {
  return sha256.slice(0, 12)
}

/** The file name a version is saved under, the same the server names it. */
function versionFileName(version: number): string {
  const dot = props.fileName.lastIndexOf('.')
  if (dot <= 0) return `${props.fileName}-v${version}`
  return `${props.fileName.slice(0, dot)}-v${version}${props.fileName.slice(dot)}`
}

async function download(version: number) {
  if (!props.versionUrl) return
  await downloadAuthed(props.versionUrl(version), versionFileName(version))
}
</script>

<template>
  <div class="space-y-2" data-testid="document-sealed-versions">
    <MutedText size="sm" tag="p">
      <AppIcon :icon="['fas', 'lock']" class="mr-1"/>{{ t('documents.sealedNote') }}
    </MutedText>
    <FieldLabel>{{ t('documents.sealedVersions') }}</FieldLabel>
    <ul class="space-y-1 text-sm">
      <li v-for="version in props.versions" :key="version.version" class="flex flex-wrap items-center gap-2">
        <span class="font-medium">{{ t('documents.sealedVersion', {version: version.version}) }}</span>
        <SuccessBadge v-if="!version.supersededAt">{{ t('documents.sealedCurrent') }}</SuccessBadge>
        <MutedText size="sm">
          {{ formatDateTime(version.sealedAt) }} · {{ t(`documents.sealLevel.${version.sealLevel}`) }}
          · {{ formatSize(version.sizeBytes) }} · {{ t('documents.sealedHash', {hash: shortHash(version.sha256)}) }}
        </MutedText>
        <MutedText v-if="version.supersededAt" size="sm">
          {{ t('documents.sealedSuperseded', {date: formatDateTime(version.supersededAt)}) }}
        </MutedText>
        <IconButton v-if="props.versionUrl" :icon="['fas', 'download']"
                    :label="t('documents.sealedDownload', {version: version.version})"
                    :data-testid="`document-version-download-${version.version}`"
                    @click="download(version.version)"/>
      </li>
    </ul>
  </div>
</template>
