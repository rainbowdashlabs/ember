/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'

/**
 * The tick that marks an entry while several are being worked on at once.
 *
 * <p>It sits inside the entry, and the entry is a link, so the press has to be taken away from the
 * browser rather than merely stopped from travelling upward: stopping it would leave the link
 * followed and the entry opened instead of marked.
 *
 * <p>The box is the control. The wrapper only takes its press, which Space on the focused box fires
 * as well, so the wrapper itself is presentation.
 */
defineProps<{
    selected?: boolean
}>()

const emit = defineEmits<{
    pick: [event: MouseEvent]
}>()
</script>

<template>
    <div data-testid="kb-item-select" role="presentation" @click.prevent="emit('pick', $event)">
        <CheckboxInput :model-value="selected" class="pointer-events-none"/>
    </div>
</template>
