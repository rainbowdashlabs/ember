/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const props = defineProps<{
    config: Record<string, unknown>
}>()

const emit = defineEmits<{
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...props.config, ...partial})
}
</script>

<template>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
            <FieldLabel hint class="mb-1">{{ TS('addrLabel') }}</FieldLabel>
            <TextInput :model-value="(config.label as string) ?? ''" @update:model-value="patch({label: $event})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('addrLine') }}</FieldLabel>
            <TextInput :model-value="(config.addressLine as string) ?? ''" @update:model-value="patch({addressLine: $event})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('addrPostal') }}</FieldLabel>
            <TextInput :model-value="(config.postalCode as string) ?? ''" @update:model-value="patch({postalCode: $event})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('addrCity') }}</FieldLabel>
            <TextInput :model-value="(config.city as string) ?? ''" @update:model-value="patch({city: $event})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('addrCountry') }}</FieldLabel>
            <TextInput :model-value="(config.country as string) ?? ''" @update:model-value="patch({country: $event})"/>
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ TS('addrMapUrl') }}</FieldLabel>
            <TextInput :model-value="(config.mapUrl as string) ?? ''" @update:model-value="patch({mapUrl: $event})"/>
        </div>
    </div>
</template>
