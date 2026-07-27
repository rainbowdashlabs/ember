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
            throw new Error(insecure ? 'barcode-scanner-insecure-context' : 'barcode-scanner-unsupported')
        }

        const stream = await openCameraStream(options)
        await attachStreamToVideo(stream, options)

        const session = makeSession(stream, options)
        const emit = (raw: string) => {
            const value = normaliseScannedPayload(raw)
            if (value) options.onDecode(value)
        }
        if (tier === 'native') {
            session.runNative(formats, emit)
        } else {
            await session.runZxing(emit)
        }
        return {stop: session.stop}
    }

    async function openCameraStream(options: StartScanOptions): Promise<MediaStream> {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                video: {
                    facingMode: {ideal: 'environment'},
                    width: {ideal: 1920},
                    height: {ideal: 1080},
                },
            })
            enableContinuousFocus(stream).catch(() => undefined)
            return stream
        } catch (e) {
            const err = e as Error
            if (err.name === 'NotFoundError' || err.name === 'OverconstrainedError') {
                markNoCamera()
            }
            throw err
        }
    }

    /**
     * Switches the camera track to continuous autofocus where the device supports it, so
     * close-up barcodes sharpen without waiting for a manual tap-to-focus. Failures are
     * swallowed because focus tuning is a best-effort improvement, never a requirement.
     */
    async function enableContinuousFocus(stream: MediaStream) {
        const track = stream.getVideoTracks()[0]
        if (!track || typeof track.getCapabilities !== 'function') return
        const capabilities = track.getCapabilities() as MediaTrackCapabilities & {focusMode?: string[]}
        if (!capabilities.focusMode?.includes('continuous')) return
        await track
            .applyConstraints({advanced: [{focusMode: 'continuous'} as MediaTrackConstraintSet]})
            .catch(() => undefined)
    }

    async function attachStreamToVideo(stream: MediaStream, options: StartScanOptions) {
        options.videoEl.srcObject = stream
        options.videoEl.setAttribute('playsinline', 'true')
        try {
            await options.videoEl.play()
        } catch (e) {
            stream.getTracks().forEach(t => t.stop())
            throw e
        }
        await waitForFreshFrame(options.videoEl)
    }

    function waitForFreshFrame(videoEl: HTMLVideoElement): Promise<void> {
        const rvfc = (videoEl as unknown as {
            requestVideoFrameCallback?: (cb: () => void) => number
        }).requestVideoFrameCallback
        return new Promise(resolve => {
            let done = false
            const finish = () => {
                if (done) return
                done = true
                resolve()
            }
            const timer = setTimeout(finish, 1000)
            const wrap = () => {
                clearTimeout(timer)
                finish()
            }
            if (typeof rvfc === 'function') {
                rvfc.call(videoEl, wrap)
                return
            }
            if (videoEl.readyState >= 2) {
                wrap()
                return
            }
            videoEl.addEventListener('loadeddata', wrap, {once: true})
        })
    }

    function makeSession(stream: MediaStream, options: StartScanOptions) {
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
            try { options.videoEl.load() } catch { /* ignore */ }
        }

        function runNative(formats: BarcodeFormat[], emit: (raw: string) => void) {
            const ctor = (globalThis as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector!
            const detector = new ctor({formats})
            nativeTimer = setInterval(async () => {
                if (stopped) return
                try {
                    const results = await detector.detect(options.videoEl)
                    if (results.length > 0 && !stopped) emit(results[0].rawValue)
                } catch {
                    /* transient frame failures are normal */
                }
            }, 200)
        }

        async function runZxing(emit: (raw: string) => void) {
            const zxingReader = await loadZxingReader()
            zxingControls = await zxingReader.decodeFromVideoElement(options.videoEl, (result) => {
                if (stopped || !result) return
                emit(result.getText())
            })
        }

        return {stop, runNative, runZxing}
    }

    return {
        startScan,
        noCameraAvailable: readonly(noCameraAvailable),
        tier: readonly(tierCache),
    }
}
