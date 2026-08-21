/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PublicFormCell from '../PublicFormCell.vue'
import type {PollEmbedConfig} from '@/api/pageManage'

defineProps<{
    config: PollEmbedConfig
    stationUid?: string | null
}>()
</script>

<template>
    <PublicFormCell
        :station-uid="stationUid ?? null"
        :form-public-uid="config.formPublicUid ?? null"
        variant="poll"
    />
</template>
