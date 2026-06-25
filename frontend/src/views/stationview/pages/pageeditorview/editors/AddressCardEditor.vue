/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import AddressField from './addresscardeditor/AddressField.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <AddressField :label="TS('addrLabel')" :model-value="(config.label as string) ?? ''" @update:model-value="patch({label: $event})"/>
        <AddressField :label="TS('addrLine')" :model-value="(config.addressLine as string) ?? ''" @update:model-value="patch({addressLine: $event})"/>
        <AddressField :label="TS('addrPostal')" :model-value="(config.postalCode as string) ?? ''" @update:model-value="patch({postalCode: $event})"/>
        <AddressField :label="TS('addrCity')" :model-value="(config.city as string) ?? ''" @update:model-value="patch({city: $event})"/>
        <AddressField :label="TS('addrCountry')" :model-value="(config.country as string) ?? ''" @update:model-value="patch({country: $event})"/>
        <AddressField :label="TS('addrMapUrl')" :model-value="(config.mapUrl as string) ?? ''" @update:model-value="patch({mapUrl: $event})"/>
    </div>
</template>
