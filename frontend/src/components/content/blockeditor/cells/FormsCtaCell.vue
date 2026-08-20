/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PublicFormCell from '../PublicFormCell.vue'
import type {FormsCtaConfig} from '@/api/pageManage'

defineProps<{
    config: FormsCtaConfig
    stationUid?: string | null
}>()
</script>

<template>
    <PublicFormCell
        :station-uid="stationUid ?? null"
        :form-public-uid="config.formPublicUid ?? null"
        :headline-override="config.headlineOverride ?? null"
        :body-override="config.bodyOverride ?? null"
        variant="contact"
    />
</template>
