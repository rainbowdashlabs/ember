/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useDebounceFn} from '@vueuse/core'
import {adminAccounts} from '@/api'
import type {AccountOverview} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'

/** How many accounts one page of the list holds. */
export const ACCOUNT_PAGE_SIZE = 25

const SEARCH_DELAY_MS = 300

/**
 * The instance's account list, a page at a time: the search and the paging are the server's, so the
 * list stays the same size however many people the instance holds. A new search starts on the
 * first page again.
 */
export function useAccountList() {
    const {t} = useI18n()
    const query = ref('')
    const page = ref(0)
    const total = ref(0)
    const accounts = ref<AccountOverview[]>([])
    const loading = ref(false)
    const failure = ref<Failure | null>(null)

    const pages = computed(() => Math.max(1, Math.ceil(total.value / ACCOUNT_PAGE_SIZE)))

    async function load() {
        loading.value = true
        failure.value = null
        try {
            const answer = await adminAccounts.listAccounts({q: query.value.trim(), page: page.value, size: ACCOUNT_PAGE_SIZE})
            accounts.value = answer.accounts
            total.value = answer.total
        } catch (e) {
            failure.value = describeFailure(e, t)
        } finally {
            loading.value = false
        }
    }

    const searchLater = useDebounceFn(() => {
        page.value = 0
        return load()
    }, SEARCH_DELAY_MS)

    function goTo(target: number) {
        page.value = Math.min(Math.max(target, 0), pages.value - 1)
        return load()
    }

    return {query, page, pages, total, accounts, loading, failure, load, search: searchLater, goTo}
}
