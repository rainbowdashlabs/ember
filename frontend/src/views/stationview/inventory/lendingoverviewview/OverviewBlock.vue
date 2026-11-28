/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import LinkButton from '@/components/button/LinkButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {BLOCK_ROWS} from './overviewBlocks'

/**
 * One block of the overview: a heading with its count and at most a few rows, opened in place to all
 * of them on request. The slot is handed how many rows to show.
 */
const props = defineProps<{
  title: string
  count: number
  empty: string
  /** Whether every row shows from the start, for a block that is the whole page to its reader. */
  whole?: boolean
}>()

defineSlots<{
  default(props: {limit: number}): unknown
}>()

const {t} = useI18n()
const open = ref(false)
const limit = computed(() => props.whole || open.value ? Number.POSITIVE_INFINITY : BLOCK_ROWS)
</script>

<template>
  <section class="space-y-1">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>{{ title }} <MutedText>({{ count }})</MutedText></SubHeader>
      <LinkButton v-if="!whole && count > BLOCK_ROWS" :aria-expanded="open" :aria-label="t(open ? 'lendingOverview.showFewerOf' : 'lendingOverview.showAllOf', {block: title})"
                  @click="open = !open">
        {{ open ? t('lendingOverview.showFewer') : t('lendingOverview.showAll') }}
      </LinkButton>
    </div>
    <MutedText v-if="count === 0" tag="p">{{ empty }}</MutedText>
    <ul v-else class="divide-y divide-(--border)">
      <slot :limit="limit"/>
    </ul>
  </section>
</template>
