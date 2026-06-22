/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'

export type BarcodeFormat =
    | 'qr_code'
    | 'data_matrix'
    | 'code_128'
    | 'code_39'
    | 'ean_13'
    | 'ean_8'
    | 'upc_a'
    | 'upc_e'

export const DEFAULT_FORMATS: BarcodeFormat[] = [
    'qr_code',
    'data_matrix',
    'code_128',
    'code_39',
    'ean_13',
    'ean_8',
    'upc_a',
    'upc_e',
]

export type ScannerTier = 'native' | 'zxing' | 'unsupported'

interface BarcodeDetectorCtor {
    new (options?: { formats?: string[] }): BarcodeDetectorInstance
    getSupportedFormats(): Promise<string[]>
}

interface BarcodeDetectorInstance {
    detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>
}

/**
 * Module-level state so a `NotFoundError` on one mount disables the scan button for the
 * rest of the session without touching localStorage — a desktop with a webcam plugged in
 * between reloads still re-detects on the next page load.
 */
const noCameraAvailable = ref(false)
const tierCache = ref<ScannerTier | null>(null)

/**
 * Strips Code 39 sentinel asterisks, trims whitespace and upper-cases using the invariant
 * locale so a hand-typed `internal_id` matches a scanned value byte-for-byte regardless of
 * which decoder produced it.
 */
export function normaliseScannedPayload(raw: string): string {
    let v = raw
    if (v.startsWith('*')) v = v.slice(1)
    if (v.endsWith('*')) v = v.slice(0, -1)
    return v.trim().toLocaleUpperCase('en-US')
}

async function probeTier(formats: BarcodeFormat[]): Promise<ScannerTier> {
    if (tierCache.value) return tierCache.value
    const ctor = (globalThis as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector
    if (ctor) {
        try {
            const supported = await ctor.getSupportedFormats()
            const required = new Set<string>(formats)
            const intersection = supported.filter(f => required.has(f as BarcodeFormat))
            if (intersection.length > 0) {
                tierCache.value = 'native'
                return 'native'
            }
        } catch { /* fall through */ }
    }
    if (typeof navigator !== 'undefined' && navigator.mediaDevices?.getUserMedia) {
        tierCache.value = 'zxing'
        return 'zxing'
    }
    tierCache.value = 'unsupported'
    return 'unsupported'
}

type ZxingControls = { stop(): void }
type ZxingReader = {
    decodeFromVideoElement(
        videoEl: HTMLVideoElement,
        callback: (result: { getText(): string } | undefined, err: unknown, controls: ZxingControls) => void,
    ): Promise<ZxingControls>
}

async function loadZxingReader(): Promise<ZxingReader> {
    const mod = await import('@zxing/browser')
    const reader = new mod.BrowserMultiFormatReader()
    return reader as unknown as ZxingReader
}

export interface ScannerSession {
    stop(): void
}

export interface StartScanOptions {
    videoEl: HTMLVideoElement
    formats?: BarcodeFormat[]
    onDecode: (value: string) => void
    onError?: (error: Error) => void
}

/**
 * Composable that hides the native-vs-zxing choice behind a single {@link startScan}
 * call. The returned {@link ScannerSession} owns the camera stream and the decoder
 * loop; calling {@code stop()} releases both. Always call it on modal close.
 */
export function useBarcodeScanner() {
    function markNoCamera() {
        noCameraAvailable.value = true
    }

    async function startScan(options: StartScanOptions): Promise<ScannerSession> {
        const formats = options.formats ?? DEFAULT_FORMATS
        const tier = await probeTier(formats)
        if (tier === 'unsupported') {
            const insecure = typeof window !== 'undefined' && window.isSecureContext === false
            const err = new Error(insecure ? 'barcode-scanner-insecure-context' : 'barcode-scanner-unsupported')
            options.onError?.(err)
            throw err
        }

        let stream: MediaStream
        try {
            stream = await navigator.mediaDevices.getUserMedia({
                video: {facingMode: {ideal: 'environment'}},
            })
        } catch (e) {
            const err = e as Error
            if (err.name === 'NotFoundError' || err.name === 'OverconstrainedError') {
                markNoCamera()
            }
            options.onError?.(err)
            throw err
        }

        options.videoEl.srcObject = stream
        options.videoEl.setAttribute('playsinline', 'true')
        try {
            await options.videoEl.play()
        } catch (e) {
            stream.getTracks().forEach(t => t.stop())
            options.onError?.(e as Error)
            throw e
        }

        let stopped = false
        let zxingControls: ZxingControls | null = null
        let nativeTimer: ReturnType<typeof setInterval> | null = null

        function stop() {
            if (stopped) return
            stopped = true
            if (nativeTimer !== null) {
                clearInterval(nativeTimer)
                nativeTimer = null
            }
            if (zxingControls) {
                try { zxingControls.stop() } catch { /* ignore */ }
                zxingControls = null
            }
            stream.getTracks().forEach(t => t.stop())
            options.videoEl.srcObject = null
        }

        function emit(raw: string) {
            const value = normaliseScannedPayload(raw)
            if (!value) return
            options.onDecode(value)
        }

        if (tier === 'native') {
            const ctor = (globalThis as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector!
            const detector = new ctor({formats})
            nativeTimer = setInterval(async () => {
                if (stopped) return
                try {
                    const results = await detector.detect(options.videoEl)
                    if (results.length > 0 && !stopped) {
                        emit(results[0].rawValue)
                    }
                } catch { /* transient frame failures are normal */ }
            }, 200)
        } else {
            const zxingReader = await loadZxingReader()
            zxingControls = await zxingReader.decodeFromVideoElement(options.videoEl, (result) => {
                if (stopped || !result) return
                emit(result.getText())
            })
        }

        return {stop}
    }

    return {
        startScan,
        noCameraAvailable: readonly(noCameraAvailable),
        tier: readonly(tierCache),
    }
}
