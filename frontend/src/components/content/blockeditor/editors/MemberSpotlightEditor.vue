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
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {resolveMemberOption, searchMemberOptions} from '@/components/input/select/memberSearchSource'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('memberName') }}</FieldLabel>
    <MemberSelectInput
        :model-value="(config.memberUid as string) ?? ''"
        :search-fn="searchMemberOptions"
        :resolve-fn="resolveMemberOption"
        clearable
        @update:model-value="(v: string) => patch({memberUid: v || null})"
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
