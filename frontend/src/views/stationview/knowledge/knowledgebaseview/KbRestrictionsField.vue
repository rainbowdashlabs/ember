/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import {StationUserTypeLabels, type MemberGroup, type StationUserTypeName, type UserTag} from '@/api/types'
import {KbAccessLevel, type KbAccessLevelName} from '@/api/knowledgeBase'
import {groupKey, tagKey, userTypeKey, type GrantLevels} from './kbGrantLevels'

const {t} = useI18n()

const props = defineProps<{
    allGroups: MemberGroup[]
    allTags: UserTag[]
}>()

const model = defineModel<RestrictionSelection>({required: true})

/**
 * The level each chosen audience entry holds. Empty means the entry only names an audience and
 * leaves what they may do to the station permission each of them holds — which is how every entry
 * behaved before levels existed.
 */
const levels = defineModel<GrantLevels>('levels', {required: true})

/**
 * No "no access" entry here on purpose. A denial aimed at an audience that is listed to be granted
 * access reads as a contradiction, and setting it on every entry would lock the whole station out
 * of the item.
 */
const LEVELS = [
    {value: KbAccessLevel.READ, label: 'kb.accessLevels.read'},
    {value: KbAccessLevel.WRITE, label: 'kb.accessLevels.write'},
    {value: KbAccessLevel.MANAGE, label: 'kb.accessLevels.manage'},
]

interface LevelEntry {
    key: string
    label: string
}

const entries = computed<LevelEntry[]>(() => [
    ...model.value.userTypes.map(userType => ({
        key: userTypeKey(userType),
        label: StationUserTypeLabels[userType as StationUserTypeName] ?? userType,
    })),
    ...model.value.groupIds.map(groupId => ({
        key: groupKey(groupId),
        label: props.allGroups.find(group => group.id === groupId)?.name ?? String(groupId),
    })),
    ...model.value.tagIds.map(tagId => ({
        key: tagKey(tagId),
        label: props.allTags.find(tag => tag.id === tagId)?.name ?? String(tagId),
    })),
])

function setLevel(key: string, value: string) {
    levels.value = {...levels.value, [key]: value ? value as KbAccessLevelName : null}
}
</script>

<template>
    <div class="space-y-3 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
        <SubHeader class="text-sm">{{ t('kb.restrictions') }}</SubHeader>
        <RestrictionsField
            :groups="allGroups"
            :tags="allTags"
            v-model="model"
        />

        <div v-if="entries.length > 0">
            <FieldLabel class="mb-1">{{ t('kb.accessLevel') }}</FieldLabel>
            <div class="space-y-2">
                <div
                    v-for="entry in entries"
                    :key="entry.key"
                    class="flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-2"
                >
                    <span class="text-sm truncate sm:w-40 flex-shrink-0">{{ entry.label }}</span>
                    <SelectInput
                        class="flex-1"
                        :model-value="levels[entry.key] ?? ''"
                        @update:model-value="value => setLevel(entry.key, String(value))"
                    >
                        <option value="">{{ t('kb.accessLevels.inherit') }}</option>
                        <option v-for="level in LEVELS" :key="level.value" :value="level.value">
                            {{ t(level.label) }}
                        </option>
                    </SelectInput>
                </div>
            </div>
            <FieldHint>{{ t('kb.accessLevelHint') }}</FieldHint>
        </div>
    </div>
</template>
