/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {SelfCheckIdentifierMatch} from '@/api/selfChecks'

/**
 * What the number the member read off a piece matched, shown as a finding rather than as a fact.
 *
 * <p>Nothing makes the number unique, it is compared without regard to case, and the containers
 * share the numbering with the gear. So every match is listed and the reviewer decides which of them,
 * if any, is the piece in the member's hands.
 */
defineProps<{identifier: SelfCheckIdentifierMatch}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="identifier.finding !== 'NOTHING_TYPED'" class="space-y-1" data-testid="identifier-finding">
    <MutedText size="xs">
      {{ t('selfCheck.review.typed', {typed: identifier.typed}) }}
      <span :data-testid="`identifier-${identifier.finding}`">{{ t(`selfCheck.review.finding.${identifier.finding}`) }}</span>
    </MutedText>
    <ul v-if="identifier.pieces.length > 0" class="text-xs text-(--text-muted) list-disc ps-5">
      <li v-for="piece in identifier.pieces" :key="piece.itemId">
        {{ piece.name }} ({{ piece.inventoryName }})
        <template v-if="piece.heldBy">{{ t('selfCheck.review.heldBy', {name: piece.heldByName}) }}</template>
        <template v-else>{{ t('selfCheck.review.free') }}</template>
      </li>
    </ul>
    <ul v-if="identifier.containers.length > 0" class="text-xs text-(--text-muted) list-disc ps-5">
      <li v-for="name in identifier.containers" :key="name">
        {{ t('selfCheck.review.container', {name}) }}
      </li>
    </ul>
  </div>
</template>
