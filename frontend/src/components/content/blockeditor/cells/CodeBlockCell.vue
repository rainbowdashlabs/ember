/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {CodeBlockConfig} from '@/api/pageManage'

defineProps<{
    config: CodeBlockConfig
    content: string
}>()
</script>

<template>
    <pre class="rounded-theme border border-(--border) bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 p-3 overflow-x-auto text-xs"><code :class="config.language ? `language-${config.language}` : ''">{{ content }}</code></pre>
</template>
