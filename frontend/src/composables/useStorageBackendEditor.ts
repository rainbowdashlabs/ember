/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {BackendRequest, BackendSummary, ProbeResult} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeFailure} from '@/util/failure'
import {
    newS3,
    newSftp,
    newSmb,
    s3FormFrom,
    sftpFormFrom,
    smbFormFrom,
    type S3Form,
    type SftpForm,
    type SmbForm,
} from '@/util/storageBackendForm'

/** Where a storage screen may point its owner's files: a kind the form offers to pick. */
export type StorageBackendChoice = BackendRequest['type']

/**
 * How one owner's storage screen reaches the server: the station's, the association's or the instance's.
 * Everything else about editing a storage is the same on all three.
 */
export interface StorageBackendApi {
    /** Whether the storage the owner keeps answers. */
    probeSaved: () => Promise<ProbeResult>
    /** Whether storage typed in but not saved would answer. */
    probeTyped: (request: BackendRequest) => Promise<ProbeResult>
    /** Reads the screen again after a change went through. */
    reload: () => Promise<unknown>
    /**
     * Reads only the history again after a change was refused or failed, or the saved storage was tested,
     * all of which the server writes down, leaving what the reader typed in place. Absent where the screen
     * shows no history.
     */
    reloadHistory?: () => Promise<unknown>
}

/** A change that moves or drops files, waiting for the reader to confirm it. */
export interface PendingStorageChange {
    /** What the confirmation says will happen. */
    body: string
    /** The change, answering with what to tell the reader once it went through. */
    act: () => Promise<string>
}

/**
 * The editing half of a storage screen, the same for a station, an association and the instance: the form
 * state and its seeding from what is stored, the request it describes, both connection tests, and every
 * change run through one confirmation where it moves or drops files, then one success message and a fresh
 * read. A change that was refused or failed reads the history again, since the server records those too.
 *
 * A failed connection test is shown as a failed test, not as an error of the screen: what the reader asked
 * was whether the storage answers, and "no" is the answer.
 */
export function useStorageBackendEditor(api: StorageBackendApi, initial: StorageBackendChoice) {
    const {t} = useI18n()
    const selectedType = ref<StorageBackendChoice>(initial)
    const localRoot = ref('data')
    const s3 = ref<S3Form>(newS3())
    const smb = ref<SmbForm>(newSmb())
    const sftp = ref<SftpForm>(newSftp())
    const savedOutcome = ref<ProbeResult | null>(null)
    const typedOutcome = ref<ProbeResult | null>(null)
    const success = ref('')
    const pending = ref<PendingStorageChange | null>(null)

    /** Fills the form from what the owner keeps; with nothing kept, the form starts at the given choice. */
    function seed(summary: BackendSummary | null | undefined, fallback: StorageBackendChoice) {
        if (!summary) {
            selectedType.value = fallback
            return
        }
        selectedType.value = summary.type
        if (summary.type === 'LOCAL') localRoot.value = summary.root || 'data'
        if (summary.type === 'S3') s3.value = s3FormFrom(summary)
        if (summary.type === 'SMB') smb.value = smbFormFrom(summary)
        if (summary.type === 'SFTP') sftp.value = sftpFormFrom(summary)
    }

    /** The storage the form describes, as the server reads it. */
    function currentRequest(): BackendRequest {
        if (selectedType.value === 'LOCAL') return {type: 'LOCAL', root: localRoot.value || 'data'}
        if (selectedType.value === 'CLUSTER') return {type: 'CLUSTER'}
        if (selectedType.value === 'S3') return s3.value
        if (selectedType.value === 'SMB') return smb.value
        return sftp.value
    }

    function failedProbe(e: unknown): ProbeResult {
        return {healthy: false, error: describeFailure(e, t).message, checkedAt: new Date().toISOString()}
    }

    const {running: probing, run: runProbe} = useAsyncAction(
        async (call: () => Promise<ProbeResult>, into: typeof savedOutcome) => {
            into.value = null
            try {
                into.value = await call()
            } catch (e) {
                into.value = failedProbe(e)
            }
        },
    )

    /** Tests the saved storage, which the server writes to the history, so the history is read again after. */
    async function probeSaved() {
        await runProbe(() => api.probeSaved(), savedOutcome)
        await api.reloadHistory?.().catch(() => undefined)
    }

    function probeTyped() {
        return runProbe(() => api.probeTyped(currentRequest()), typedOutcome)
    }

    const {running: saving, failure, run: runChange} = useAsyncAction(async (act: () => Promise<string>) => {
        success.value = ''
        try {
            success.value = await act()
        } catch (e) {
            await api.reloadHistory?.().catch(() => undefined)
            throw e
        }
        await api.reload()
    })

    /** Runs a change that moves no files, at once. */
    function perform(act: () => Promise<string>) {
        return runChange(act)
    }

    /** Holds a change that moves or drops files until the reader confirms it. */
    function askFirst(body: string, act: () => Promise<string>) {
        pending.value = {body, act}
    }

    function confirmPending() {
        const change = pending.value
        pending.value = null
        return change ? runChange(change.act) : Promise.resolve(undefined)
    }

    function cancelPending() {
        pending.value = null
    }

    return {
        selectedType,
        localRoot,
        s3,
        smb,
        sftp,
        savedOutcome,
        typedOutcome,
        success,
        pending,
        probing,
        saving,
        failure,
        seed,
        currentRequest,
        probeSaved,
        probeTyped,
        perform,
        askFirst,
        confirmPending,
        cancelPending,
    }
}
