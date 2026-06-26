/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CellImagePicker from '../CellImagePicker.vue'
import {GalleryAspectMode, type GalleryItem} from '@/api/pageManage'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorPageProps} from '../cellTypes'

const props = defineProps<CellEditorPageProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
            <FieldLabel hint class="mb-1">{{ TS('galleryAspectMode') }}</FieldLabel>
            <SelectInput :model-value="(config.aspectMode as string) ?? GalleryAspectMode.SQUARE" @update:model-value="patch({aspectMode: $event})">
                <option :value="GalleryAspectMode.SQUARE">{{ TS('galleryAspectSquare') }}</option>
                <option :value="GalleryAspectMode.PRESERVE">{{ TS('galleryAspectPreserve') }}</option>
            </SelectInput>
        </div>
        <div v-if="config.aspectMode !== GalleryAspectMode.PRESERVE">
            <FieldLabel hint class="mb-1">{{ TS('galleryColumns') }}</FieldLabel>
            <NumberInput :model-value="(config.columns as number) ?? 3" @update:model-value="patch({columns: $event || null})"/>
        </div>
        <div v-else>
            <FieldLabel hint class="mb-1">{{ TS('galleryMaxItemHeight') }}</FieldLabel>
            <NumberInput :model-value="(config.maxItemHeightPx as number) ?? 300" @update:model-value="patch({maxItemHeightPx: $event || null})"/>
        </div>
    </div>
    <FieldLabel hint class="mb-1 mt-2">{{ TS('galleryImages') }}</FieldLabel>
    <CellImagePicker
        multi
        :items="config.items as GalleryItem[]"
        :page-id="pageId"
        :station-uid="stationUid"
        @update:items="patch({items: $event})"
    />
</template>
