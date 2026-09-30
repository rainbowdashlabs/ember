/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {groupSets} from '@/api'
import type {MemberGroupSet} from '@/api/groupSets'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The station's sets of groups and the writes the groups page makes to them: naming a new one,
 * renaming one and deleting one. Deleting a set keeps its groups, which is why the caller reloads them.
 *
 * @param failure the page's failure channel
 * @param onDeleted what to do once a set is gone, which is reloading the groups that were in it
 */
export function useGroupSets(failure: Ref<Failure | null>, onDeleted: () => Promise<void>) {
    const {t} = useI18n()
    const sets = ref<MemberGroupSet[]>([])

    async function run(write: () => Promise<unknown>) {
        failure.value = null
        try {
            await write()
            sets.value = await groupSets.listSets()
        } catch (e) {
            failure.value = describeFailure(e, t)
        }
    }

    return {
        sets,
        load: () => run(async () => {}),
        create: (name: string) => run(() => groupSets.createSet(name.trim())),
        rename: (id: number, name: string) => run(() => groupSets.renameSet(id, name.trim())),
        remove: (id: number) => run(async () => {
            await groupSets.deleteSet(id)
            await onDeleted()
        }),
    }
}
