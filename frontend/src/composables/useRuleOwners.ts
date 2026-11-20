/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {memberRules} from '@/api'

/** How long after a saved rule the members are read again, once the station's rules have run. */
const RULE_SETTLE_MS = 4000

/**
 * Which groups and tags of the station take their members from a rule, so a screen can mark them and keep
 * their members from being changed by hand. Until the list has loaded, or where it cannot be read, nothing
 * counts as rule-kept; the server refuses a hand change to one regardless.
 */
export function useRuleOwners() {
    const groupIds = ref<number[]>([])
    const tagIds = ref<number[]>([])

    async function load() {
        try {
            const owners = await memberRules.listOwners()
            groupIds.value = owners.groupIds
            tagIds.value = owners.tagIds
        } catch {
            groupIds.value = []
            tagIds.value = []
        }
    }

    /**
     * A rule was saved or removed: the markers are read again at once, the members once the station's rules have
     * run, a few seconds after the save.
     *
     * @param rereadMembers reads the open group's or tag's members again
     */
    function changed(rereadMembers: () => void) {
        void load()
        window.setTimeout(rereadMembers, RULE_SETTLE_MS)
    }

    const ruledGroups = computed(() => new Set(groupIds.value))
    const ruledTags = computed(() => new Set(tagIds.value))

    return {
        load,
        changed,
        ruledGroups,
        ruledTags,
        isGroupRuled: (id: number) => ruledGroups.value.has(id),
        isTagRuled: (id: number) => ruledTags.value.has(id),
    }
}
