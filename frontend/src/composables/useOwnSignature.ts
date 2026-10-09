/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, readonly, ref} from 'vue'
import type {SignatureImageSource, SignatureSettingsResponse} from '@/api/generated/schema'
import {
    deleteSignatureImage,
    getSignatureImage,
    getSignatureSettings,
    saveSignatureImage,
    setSignatureConsent,
} from '@/api/signing'

/**
 * The reader's own signature: the saved picture, ready to show, and the consent to letters being signed
 * with it.
 *
 * <p>The picture is held as an object address of its bytes, given back to the browser whenever it is
 * replaced and when the component goes, so a page that saves a few pictures in a row keeps one.
 */
export function useOwnSignature() {
    const settings = ref<SignatureSettingsResponse | null>(null)
    const imageUrl = ref<string | null>(null)

    function show(image: Blob | null) {
        if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
        imageUrl.value = image ? URL.createObjectURL(image) : null
    }

    /** Reads the saved picture alone, for a screen that only shows it. */
    async function loadImage() {
        show(await getSignatureImage())
    }

    /** Reads what the account keeps and the picture with it. */
    async function load() {
        settings.value = await getSignatureSettings()
        show(settings.value.hasImage ? await getSignatureImage() : null)
    }

    /**
     * Saves a picture as the reader's own and shows it as the server cleaned it.
     *
     * @param image  the picture
     * @param source how it was made
     */
    async function save(image: Blob, source: SignatureImageSource) {
        settings.value = await saveSignatureImage(image, source)
        await loadImage()
    }

    async function remove() {
        settings.value = await deleteSignatureImage()
        show(null)
    }

    /** @param consented whether letters the reader issues are signed with their picture from now on */
    async function consent(consented: boolean) {
        settings.value = await setSignatureConsent(consented)
    }

    onBeforeUnmount(() => show(null))

    return {
        settings: readonly(settings),
        imageUrl: readonly(imageUrl),
        load,
        loadImage,
        save,
        remove,
        consent,
    }
}
