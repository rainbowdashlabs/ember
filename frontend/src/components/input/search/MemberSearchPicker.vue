/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {getMemberPickerByUid, searchMembers, type MemberSearchResult} from '@/api/members'

const model = defineModel<string | null>()

const props = defineProps<{
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
}>()

const emit = defineEmits<{
    pick: [item: MemberSearchResult]
}>()

const {t} = useI18n()

const searchFn = (q: string) => searchMembers(q, 20)
const displayFn = (item: MemberSearchResult) => item.displayName
const userTypeI18nKey: Record<string, string> = {
    MEMBER: 'memberEdit.userTypeMember',
    GUARDIAN: 'memberEdit.userTypeGuardian',
    TEAM: 'memberEdit.userTypeTeam',
    MANAGER: 'memberEdit.userTypeManager',
    TRIAL: 'memberEdit.userTypeTrial',
}
const subtitleFn = (item: MemberSearchResult) => {
    if (!item.userType) return ''
    const key = userTypeI18nKey[item.userType]
    return key ? t(key) : item.userType
}
const keyFn = (item: MemberSearchResult) => item.memberUid
const iconFn = (): string[] => ['fas', 'user']
const avatarFn = (item: MemberSearchResult): string | null => item.avatarUrl

const resolvedName = ref<string | null>(null)
async function resolve() {
    if (!model.value) { resolvedName.value = null; return }
    try {
        const m = await getMemberPickerByUid(model.value)
        resolvedName.value = m?.displayName ?? null
    } catch { resolvedName.value = null }
}
onMounted(resolve)
watch(() => model.value, resolve)
void props
</script>

<template>
    <EntitySearchPicker
        v-model="model"
        :search-fn="searchFn"
        :display-fn="displayFn"
        :subtitle-fn="subtitleFn"
        :key-fn="keyFn"
        :icon-fn="iconFn"
        :avatar-fn="avatarFn"
        :selected-display="resolvedName ?? selectedDisplay"
        :placeholder="placeholder ?? t('stationPages.editor.memberSearchPlaceholder')"
        :disabled="disabled"
        @pick="(it: MemberSearchResult) => emit('pick', it)"
    />
</template>
