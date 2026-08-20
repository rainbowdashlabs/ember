/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LinkSearchInput from '@/components/input/text/LinkSearchInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorStationProps} from '../cellTypes'

const props = defineProps<CellEditorStationProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('pdfUrl') }}</FieldLabel>
    <LinkSearchInput :model-value="(config.url as string) ?? ''" :station-uid="stationUid" mime-prefix="application/pdf" @update:model-value="patch({url: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('pdfHeight') }}</FieldLabel>
    <NumberInput :model-value="(config.heightPx as number) ?? 600" @update:model-value="patch({heightPx: $event || null})"/>
</template>
