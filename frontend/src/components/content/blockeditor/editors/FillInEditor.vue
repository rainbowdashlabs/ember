/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import FillInSettings from '@/components/documents/FillInSettings.vue'
import {SignatureRole} from '@/api/generated/schema'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'

/**
 * A box in a letter that a signer fills in when they sign: whose signature it goes with, its label,
 * whether it is required and how long its value may be. A new box starts with the participant, who is the
 * one most often asked for their own details. Settings left at their default are not kept.
 */
const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const patch = useConfigPatch(() => props.config, emit)

onMounted(() => {
    if (!props.config.signer) patch({signer: SignatureRole.PARTICIPANT})
})
</script>

<template>
    <FillInSettings :signer="(config.signer as SignatureRole | undefined)"
                    :label="(config.label as string | undefined) ?? ''"
                    :required="config.required === true"
                    :max-length="(config.maxLength as number | undefined)"
                    @update:signer="patch({signer: $event ?? undefined})"
                    @update:label="patch({label: $event})"
                    @update:required="patch({required: $event || undefined})"
                    @update:max-length="patch({maxLength: $event ?? undefined})"/>
</template>
