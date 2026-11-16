/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from './SubHeader.vue'
import HintIcon from './HintIcon.vue'

/**
 * A section's heading with what the section is for behind a hint icon beside it, for forms where a
 * sentence under every heading would make the page twice as long.
 */
defineProps<{
  /** What the section is for, read out with the icon and shown when it is reached. */
  hint: string
}>()
</script>

<template>
  <div class="flex items-center gap-1">
    <SubHeader>
      <slot/>
    </SubHeader>
    <HintIcon :text="hint"/>
  </div>
</template>
