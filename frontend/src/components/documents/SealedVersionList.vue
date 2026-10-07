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
import AppIcon from '@/components/display/AppIcon.vue'
import type {SealedVersionResponse} from '@/api/generated/schema'
import {formatDateTime, formatSize} from '@/util/format'

/**
 * What a sealed document says about its seal: that it is locked, and every sealed version it holds,
 * newest first, the one it serves marked current and the earlier ones with the date they were
 * superseded. The hash is shortened; it names the file, it is not meant to be read in full.
 */
const props = defineProps<{
  versions: SealedVersionResponse[]
}>()

const {t} = useI18n()

/** The first characters of a hash, enough to tell two versions apart at a glance. */
function shortHash(sha256: string): string {
  return sha256.slice(0, 12)
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
      </li>
    </ul>
  </div>
</template>
