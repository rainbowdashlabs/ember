/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import StationMap, {type MapStation} from './StationMap.vue'

/**
 * The pins of the station map, drawn through a stand-in for Leaflet that records what the map was asked
 * to draw.
 *
 * @vitest-environment happy-dom
 */
interface FakeMarker {
    latLng: [number, number]
    options: {icon: {className: string}; title: string}
    tooltip: {content: string; options: {permanent: boolean; className: string}} | null
    popup: string | null
    handlers: Record<string, () => void>
}

interface FakeLayer {
    kind: string
    options: Record<string, unknown>
    markers: FakeMarker[]
}

const drawn = vi.hoisted(() => ({
    layers: [] as FakeLayer[],
    pannedTo: [] as [number, number][],
}))

function fakeLayer(kind: string, options: Record<string, unknown> = {}): FakeLayer & Record<string, unknown> {
    const layer: FakeLayer & Record<string, unknown> = {
        kind,
        options,
        markers: [],
        addTo: () => layer,
        clearLayers: () => {
            layer.markers = []
        },
        addLayer: (marker: FakeMarker) => {
            layer.markers.push(marker)
        },
    }
    drawn.layers.push(layer)
    return layer
}

const fakeLeaflet = {
    map: () => ({
        fitBounds: () => undefined,
        panTo: (latLng: [number, number]) => drawn.pannedTo.push(latLng),
        setView: () => undefined,
        getZoom: () => 5,
        remove: () => undefined,
    }),
    markerClusterGroup: (options: Record<string, unknown>) => fakeLayer('cluster', options),
    layerGroup: () => fakeLayer('plain'),
    divIcon: (options: unknown) => options,
    marker: (latLng: [number, number], options: FakeMarker['options']) => {
        const marker: FakeMarker & Record<string, unknown> = {
            latLng,
            options,
            tooltip: null,
            popup: null,
            handlers: {},
            bindTooltip: (content: string, tooltipOptions: {permanent: boolean; className: string}) => {
                marker.tooltip = {content, options: tooltipOptions}
            },
            bindPopup: (content: string) => {
                marker.popup = content
            },
            on: (event: string, handler: () => void) => {
                marker.handlers[event] = handler
            },
            getLatLng: () => latLng,
            openPopup: () => undefined,
        }
        return marker
    },
}

vi.mock('@/util/leaflet', () => ({
    loadLeaflet: async () => fakeLeaflet,
    addTileLayer: () => undefined,
}))
vi.mock('leaflet.markercluster', () => ({}))
vi.mock('@/composables/useMapsConfig', () => ({
    useMapsConfig: () => ({load: async () => ({urlTemplate: null})}),
}))

describe('StationMap', () => {
    const stations: MapStation[] = [
        {uid: 'nord', name: 'Wache <Nord>', latitude: 53.5, longitude: 10.0},
        {uid: 'sued', name: 'Wache Süd', latitude: 48.1, longitude: 11.6},
    ]

    beforeEach(() => {
        drawn.layers.length = 0
        drawn.pannedTo.length = 0
    })

    async function drawnMap(props: Record<string, unknown>) {
        const wrapper = mount(StationMap, {props: {stations, ...props}})
        await flushPromises()
        return wrapper
    }

    function cluster(): FakeLayer {
        return drawn.layers.find(layer => layer.kind === 'cluster')!
    }

    function chosenLayer(): FakeLayer {
        return drawn.layers.filter(layer => layer.kind === 'plain').at(-1)!
    }

    it('names every pin with a label that always shows, written as text', async () => {
        await drawnMap({labels: true, popups: false})

        const labels = cluster().markers.map(marker => marker.tooltip)
        expect(labels.map(label => label?.content)).toEqual(['Wache &lt;Nord&gt;', 'Wache Süd'])
        expect(labels.every(label => label?.options.permanent)).toBe(true)
    })

    it('gathers labelled pins with a radius wide enough for a name', async () => {
        await drawnMap({labels: true})

        expect(cluster().options.maxClusterRadius).toBeGreaterThan(80)
    })

    it('keeps the chosen pin outside the clusters, highlighted, so its name always shows', async () => {
        await drawnMap({labels: true, selectedUid: 'sued'})

        expect(cluster().markers.map(marker => marker.options.title)).not.toContain('Wache Süd')
        const chosen = chosenLayer().markers
        expect(chosen.map(marker => marker.options.title)).toEqual(['Wache Süd'])
        expect(chosen[0]!.options.icon.className).toContain('station-map-pin-selected')
        expect(chosen[0]!.tooltip?.options.className).toContain('station-map-label-selected')
    })

    it('moves the highlight when another pin is chosen', async () => {
        const wrapper = await drawnMap({labels: true, selectedUid: 'sued'})

        await wrapper.setProps({selectedUid: 'nord'})
        await flushPromises()

        expect(chosenLayer().markers.map(marker => marker.options.title)).toEqual(['Wache <Nord>'])
    })

    it('reports a click on a pin and opens no popup where the page shows the station itself', async () => {
        const wrapper = await drawnMap({labels: true, popups: false})

        cluster().markers[1]!.handlers.click!()

        expect(wrapper.emitted('marker-click')).toEqual([['sued']])
        expect(cluster().markers.every(marker => marker.popup === null)).toBe(true)
    })

    it('keeps its popups and leaves names off where nobody asked for labels', async () => {
        await drawnMap({})

        expect(cluster().markers.every(marker => marker.tooltip === null)).toBe(true)
        expect(cluster().markers[0]!.popup).toContain('Wache &lt;Nord&gt;')
    })

    it('centres a chosen pin without changing the zoom', async () => {
        const wrapper = await drawnMap({labels: true})

        wrapper.vm.center('nord')

        expect(drawn.pannedTo).toEqual([[53.5, 10.0]])
    })
})
