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
    created: [] as FakeMarker[],
    restyled: [] as FakeMarker[],
    framed: [] as [number, number][][],
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
        removeLayer: (marker: FakeMarker) => {
            layer.markers = layer.markers.filter(drawnMarker => drawnMarker !== marker)
        },
    }
    drawn.layers.push(layer)
    return layer
}

const fakeLeaflet = {
    map: () => ({
        fitBounds: (bounds: [number, number][]) => drawn.framed.push(bounds),
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
            unbindTooltip: () => {
                marker.tooltip = null
            },
            setIcon: (icon: {className: string}) => {
                marker.options = {...marker.options, icon}
                drawn.restyled.push(marker)
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
        drawn.created.push(marker)
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
    const nord: MapStation = {uid: 'nord', name: 'Wache <Nord>', latitude: 53.5, longitude: 10.0}
    const sued: MapStation = {uid: 'sued', name: 'Wache Süd', latitude: 48.1, longitude: 11.6}
    const stations: MapStation[] = [nord, sued]

    beforeEach(() => {
        drawn.layers.length = 0
        drawn.pannedTo.length = 0
        drawn.created.length = 0
        drawn.restyled.length = 0
        drawn.framed.length = 0
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

    it('adds only the pin of a station that joins the list and keeps the others as they are', async () => {
        const wrapper = await drawnMap({labels: true})
        const before = [...cluster().markers]

        await wrapper.setProps({stations: [...stations, {uid: 'west', name: 'Wache West', latitude: 51.2, longitude: 6.8}]})
        await flushPromises()

        expect(drawn.created).toHaveLength(3)
        expect(cluster().markers.slice(0, 2)).toEqual(before)
        expect(cluster().markers.map(marker => marker.options.title)).toEqual(['Wache <Nord>', 'Wache Süd', 'Wache West'])
    })

    it('takes off only the pin of a station that leaves the list', async () => {
        const wrapper = await drawnMap({labels: true})
        const kept = cluster().markers[1]

        await wrapper.setProps({stations: [sued]})
        await flushPromises()

        expect(drawn.created).toHaveLength(2)
        expect(cluster().markers).toEqual([kept])
    })

    it('draws a pin anew when its station changes', async () => {
        const wrapper = await drawnMap({labels: true})

        await wrapper.setProps({stations: [nord, {...sued, name: 'Wache Mitte'}]})
        await flushPromises()

        expect(drawn.created).toHaveLength(3)
        expect(cluster().markers.map(marker => marker.options.title)).toEqual(['Wache <Nord>', 'Wache Mitte'])
    })

    it('touches only the pin chosen before and the pin chosen now when the choice moves', async () => {
        const three = [...stations, {uid: 'west', name: 'Wache West', latitude: 51.2, longitude: 6.8}]
        const wrapper = await drawnMap({stations: three, labels: true, selectedUid: 'sued'})

        await wrapper.setProps({selectedUid: 'nord'})
        await flushPromises()

        expect(drawn.created).toHaveLength(3)
        expect(drawn.restyled.map(marker => marker.options.title)).toEqual(['Wache Süd', 'Wache <Nord>'])
        expect(cluster().markers.map(marker => marker.options.title)).toEqual(['Wache West', 'Wache Süd'])
        expect(cluster().markers[1]!.options.icon.className).not.toContain('station-map-pin-selected')
        expect(cluster().markers[1]!.tooltip?.options.className).not.toContain('station-map-label-selected')
        expect(chosenLayer().markers[0]!.tooltip?.options.className).toContain('station-map-label-selected')
    })

    it('frames the pins when the stations change and leaves the view alone when only the choice does', async () => {
        const wrapper = await drawnMap({labels: true})
        expect(drawn.framed).toHaveLength(1)

        await wrapper.setProps({selectedUid: 'nord'})
        await wrapper.setProps({stations: [...stations]})
        await flushPromises()
        expect(drawn.framed).toHaveLength(1)

        await wrapper.setProps({stations: [nord]})
        await flushPromises()
        expect(drawn.framed).toEqual([[[53.5, 10.0], [48.1, 11.6]], [[53.5, 10.0]]])
    })
})
