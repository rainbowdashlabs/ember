/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'
import {useConfigPatch} from '@/composables/useConfigPatch'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
            <FieldLabel hint class="mb-1">{{ TS('mapLat') }}</FieldLabel>
            <NumberInput :model-value="(config.latitude as number) ?? undefined" @update:model-value="patch({latitude: $event || null})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('mapLon') }}</FieldLabel>
            <NumberInput :model-value="(config.longitude as number) ?? undefined" @update:model-value="patch({longitude: $event || null})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('mapZoom') }}</FieldLabel>
            <NumberInput :model-value="(config.zoom as number) ?? 14" @update:model-value="patch({zoom: $event || null})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('mapHeight') }}</FieldLabel>
            <NumberInput :model-value="(config.heightPx as number) ?? 320" @update:model-value="patch({heightPx: $event || null})"/>
        </div>
    </div>
    <FieldLabel hint class="mb-1">{{ TS('mapLabel') }}</FieldLabel>
    <TextInput :model-value="(config.label as string) ?? ''" @update:model-value="patch({label: $event})"/>
</template>
