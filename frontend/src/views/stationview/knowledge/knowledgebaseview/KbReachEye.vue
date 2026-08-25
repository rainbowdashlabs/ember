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
 * <p>Green for an entry standing on the public wiki. Blue for one every partner station reads, which is a
 * different thing from the public web and a different thing again from nobody outside. Yellow for one
 * shared without being open to everyone who meets it: named stations, or particular kinds of reader.
 * Nothing at all for an entry that stays here, so the mark means something when it appears.
 *
 * <p>Inherited counts. Everything inside a public folder is public, and every one of those tiles is
 * green, because a badge answers the question for the tile it sits on rather than for a folder further
 * up that the reader may not have seen.
 */
const props = defineProps<{
    reach: 'public' | 'federated' | 'narrow'
}>()

const TONES = {
    public: 'text-success',
    federated: 'text-secondary-accent',
    narrow: 'text-warning',
} as const

const LABELS = {
    public: 'kb.reachPublic',
    federated: 'kb.reachFederated',
    narrow: 'kb.reachNarrow',
} as const

const tone = computed(() => TONES[props.reach])
const label = computed(() => t(LABELS[props.reach]))
</script>

<template>
    <span
        :title="label"
        :aria-label="label"
        :data-reach="reach"
        data-testid="kb-reach"
        class="inline-flex flex-shrink-0"
        role="img"
    >
        <font-awesome-icon :icon="['fas', 'eye']" :class="tone" class="h-3 w-3"/>
    </span>
</template>
