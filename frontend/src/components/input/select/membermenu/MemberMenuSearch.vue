/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useTemplateRef} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {StationUserTypeLabels} from '@/api/types'

/**
 * The head of a member menu: the search, and the kind of member beside it.
 *
 * <p>The search is here unconditionally, which is the rule the whole menu exists to keep. The kind is
 * not: it earns its place only where a screen lists the whole station while somebody looks for one of
 * the twelve youth members, and fewer than two kinds on offer is a choice that narrows nothing.
 */
const search = defineModel<string>('search', {required: true})
const userType = defineModel<string>('userType', {required: true})

const props = defineProps<{
  /** The kinds present among the people on offer. Fewer than two offers nothing. */
  userTypes: string[]
}>()

const {t} = useI18n()

const searchRef = useTemplateRef('searchBox')

const offered = computed(() => props.userTypes.filter(Boolean))

function typeLabel(value: string): string {
  return StationUserTypeLabels[value as keyof typeof StationUserTypeLabels] ?? value
}

defineExpose({focus: () => searchRef.value?.focus()})
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 border-b border-(--border) p-2">
    <SearchInput
        ref="searchBox"
        v-model="search"
        data-testid="member-select-search"
        :placeholder="t('memberSelect.search')"
        class="min-w-40 flex-1"
    />
    <SelectInput
        v-if="offered.length > 1"
        v-model="userType"
        data-testid="member-select-user-type"
        class="w-40"
    >
      <option value="">{{ t('memberSelect.anyUserType') }}</option>
      <option v-for="value in offered" :key="value" :value="value">{{ typeLabel(value) }}</option>
    </SelectInput>
  </div>
</template>
