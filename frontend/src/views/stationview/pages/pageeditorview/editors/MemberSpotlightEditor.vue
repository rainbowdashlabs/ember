/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MarkdownFieldInput from '@/components/input/text/MarkdownFieldInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MemberSearchPicker from '@/components/input/search/MemberSearchPicker.vue'

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
    <FieldLabel hint class="mb-1">{{ TS('memberName') }}</FieldLabel>
    <MemberSearchPicker
        :model-value="(config.memberUid as string) ?? null"
        @pick="(item: {memberUid: string}) => patch({memberUid: item.memberUid})"
        @update:model-value="(v: string | null) => patch({memberUid: v})"
    />
    <FieldLabel hint class="mb-1">{{ TS('memberBlurb') }}</FieldLabel>
    <MarkdownFieldInput :model-value="(config.blurb as string) ?? ''" @update:model-value="patch({blurb: $event ?? ''})"/>
    <div class="flex items-end gap-2 pt-1">
        <ToggleInput
            :model-value="config.showUserType !== false"
            @update:model-value="patch({showUserType: $event})"
        />
        <FieldLabel hint class="mb-0">{{ TS('showUserType') }}</FieldLabel>
    </div>
    <div class="flex items-end gap-2">
        <ToggleInput
            :model-value="!!config.showTag"
            @update:model-value="patch({showTag: $event})"
        />
        <FieldLabel hint class="mb-0">{{ TS('showTag') }}</FieldLabel>
    </div>
</template>
