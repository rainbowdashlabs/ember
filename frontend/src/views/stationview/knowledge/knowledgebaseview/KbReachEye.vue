/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

/**
 * How far an entry of the wiki reaches, in one small mark.
 *
 * <p>Green for an entry standing on the public wiki, yellow for one shared beyond this station without
 * being open to everyone in it: named stations, or particular kinds of reader. Nothing at all for the
 * ordinary case, which is most entries, so the mark means something when it appears.
 *
 * <p>Inherited counts. Everything inside a public folder is public, and every one of those tiles is
 * green, because a badge answers the question for the tile it sits on rather than for a folder further
 * up that the reader may not have seen.
 */
const props = defineProps<{
    reach: 'public' | 'narrow'
}>()

const tone = computed(() => props.reach === 'public'
    ? 'text-success'
    : 'text-warning')

const label = computed(() => props.reach === 'public'
    ? t('kb.reachPublic')
    : t('kb.reachNarrow'))
</script>

<template>
    <font-awesome-icon
        :icon="['fas', 'eye']"
        :class="tone"
        :title="label"
        :aria-label="label"
        :data-reach="reach"
        data-testid="kb-reach"
        class="h-3 w-3 flex-shrink-0"
    />
</template>
