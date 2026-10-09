/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {HeldCopy} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * Whether this installation keeps exactly the checked file, said neutrally either way: a file it does
 * not keep can still carry a good seal, since a copy may live anywhere.
 */
const props = defineProps<{
  document: HeldCopy
}>()

const {t} = useI18n()

const text = computed(() => {
  const held = props.document
  if (!held.held) return t('sealVerification.held.notHeld')
  const level = held.sealLevel ? t(`documents.sealLevel.${held.sealLevel}`) : null
  const said = held.sealedAt
    ? t('sealVerification.held.heldSince', {date: formatDateTime(held.sealedAt)})
    : t('sealVerification.held.held')
  return level ? `${said} ${t('sealVerification.held.level', {level})}` : said
})
</script>

<template>
  <div class="flex items-start gap-2" data-testid="seal-held-copy" :data-held="document.held">
    <AppIcon :icon="['fas', document.held ? 'lock' : 'circle-info']" class="mt-0.5 text-(--text-muted) shrink-0"/>
    <MutedText tag="p" size="sm">{{ text }}</MutedText>
  </div>
</template>
