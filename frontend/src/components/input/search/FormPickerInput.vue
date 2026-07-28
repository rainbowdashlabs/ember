/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {getFormPickerByUid, searchForms, type FormSearchResult} from '@/api/forms'
import {FormStatus, type FormPurposeName} from '@/api/forms'

const model = defineModel<string | null>()

const props = defineProps<{
    /** Required: filters the picker to forms of this purpose (CONTACT or POLL). */
    purpose: FormPurposeName
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
}>()

const emit = defineEmits<{
    pick: [item: FormSearchResult]
}>()

const {t} = useI18n()

const searchFn = (q: string) => searchForms(props.purpose, q, 10)
const displayFn = (item: FormSearchResult) => item.title
const subtitleFn = (item: FormSearchResult) => {
  if (item.status === FormStatus.OPEN) return t('stationPages.editor.formPickerStatusOpen')
  if (item.status === FormStatus.DRAFT) return t('stationPages.editor.formPickerStatusDraft')
  return t('stationPages.editor.formPickerStatusClosed')
}
const keyFn = (item: FormSearchResult) => item.publicUid
const iconFn = (): string[] => ['fas', 'clipboard-list']
const isSelectableFn = (item: FormSearchResult) => item.status !== FormStatus.CLOSED

const resolvedTitle = ref<string | null>(null)
async function resolve() {
    if (!model.value) { resolvedTitle.value = null; return }
    try {
        const f = await getFormPickerByUid(props.purpose, model.value)
        resolvedTitle.value = f?.title ?? null
    } catch { resolvedTitle.value = null }
}
onMounted(resolve)
watch(() => [props.purpose, model.value], resolve)

function onPick(item: FormSearchResult) {
    model.value = item.publicUid
    resolvedTitle.value = item.title
    emit('pick', item)
}
</script>

<template>
    <EntitySearchPicker
        v-model="model"
        :search-fn="searchFn"
        :display-fn="displayFn"
        :subtitle-fn="subtitleFn"
        :key-fn="keyFn"
        :icon-fn="iconFn"
        :is-selectable-fn="isSelectableFn"
        :selected-display="resolvedTitle ?? selectedDisplay"
        :placeholder="placeholder ?? t('stationPages.editor.formPickerPlaceholder')"
        :disabled="disabled"
        :not-selectable-hint="t('stationPages.editor.formPickerClosedHint')"
        @pick="onPick"
    />
</template>
