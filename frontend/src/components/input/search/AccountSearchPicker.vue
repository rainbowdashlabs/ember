/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {twoFactorAdmin} from '@/api'
import type {AccountSearchResult} from '@/api/twoFactorAdmin'

const model = defineModel<string | null>()

const props = defineProps<{
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
}>()

const emit = defineEmits<{
    pick: [item: AccountSearchResult]
}>()

const {t} = useI18n()

const searchFn = (q: string) => twoFactorAdmin.searchAccounts(q, 20)
const displayFn = (item: AccountSearchResult) => item.displayName
const subtitleFn = (item: AccountSearchResult) => item.email
const keyFn = (item: AccountSearchResult) => item.uid
const iconFn = (): string[] => ['fas', 'user']

const resolvedName = ref<string | null>(null)
async function resolve() {
    if (!model.value) {
        resolvedName.value = null
        return
    }
    try {
        const a = await twoFactorAdmin.getAccountPickerByUid(model.value)
        resolvedName.value = a ? `${a.displayName} (${a.email})` : null
    } catch {
        resolvedName.value = null
    }
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
        :selected-display="resolvedName ?? selectedDisplay"
        :placeholder="placeholder ?? t('twoFactor.admin.accountSearchPlaceholder')"
        :disabled="disabled"
        @pick="(it: AccountSearchResult) => emit('pick', it)"
    />
</template>
