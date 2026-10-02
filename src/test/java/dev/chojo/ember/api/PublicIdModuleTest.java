/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicIdModuleTest {

    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID CLUSTER_UID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    record Row(int stationId, Integer homeStationId, Integer clusterId, int count) {}

    private final StationRepository stations = mock(StationRepository.class);
    private final ClusterRepository clusters = mock(ClusterRepository.class);

    private JsonMapper apiMapper() {
        Station station = mock(Station.class);
        when(station.id()).thenReturn(7);
        Cluster cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(3);
        when(stations.resolveUid(7)).thenReturn(STATION_UID);
        when(stations.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(clusters.resolveUid(3)).thenReturn(CLUSTER_UID);
        when(clusters.findByUid(CLUSTER_UID)).thenReturn(Optional.of(cluster));
        return JsonMapper.builder()
                .addModule(PublicIdModule.forApi(stations, clusters))
                .build();
    }

    @Test
    void stationAndClusterIdsLeaveAsTheirUuids() {
        String json = apiMapper().writeValueAsString(new Row(7, null, 3, 12));

        assertEquals(
                "{\"stationId\":\"" + STATION_UID + "\",\"homeStationId\":null,\"clusterId\":\"" + CLUSTER_UID
                        + "\",\"count\":12}",
                json);
    }

    @Test
    void uuidsComeBackAsTheInternalIds() {
        Row row = apiMapper()
                .readValue(
                        "{\"stationId\":\"" + STATION_UID + "\",\"homeStationId\":null,\"clusterId\":\"" + CLUSTER_UID
                                + "\",\"count\":12}",
                        Row.class);

        assertEquals(new Row(7, null, 3, 12), row);
    }

    @Test
    void aUuidNobodyKnowsFailsTheRead() {
        String unknown = "{\"stationId\":\"" + UUID.randomUUID() + "\",\"count\":1}";

        assertThrows(JacksonException.class, () -> apiMapper().readValue(unknown, Row.class));
    }

    @Test
    void somethingThatIsNoUuidFailsTheRead() {
        assertThrows(
                JacksonException.class, () -> apiMapper().readValue("{\"clusterId\":\"nine\",\"count\":1}", Row.class));
    }

    @Test
    void anIdWithoutAStationIsWrittenAsNull() {
        String json = apiMapper().writeValueAsString(new Row(99, null, null, 0));

        assertEquals("{\"stationId\":null,\"homeStationId\":null,\"clusterId\":null,\"count\":0}", json);
    }

    @Test
    void aPartnersStationIdsAreReadAsNeutral() {
        JsonMapper partner = JsonMapper.builder()
                .addModule(PublicIdModule.forPartnerResponses())
                .build();

        Row row = partner.readValue(
                "{\"stationId\":\"" + STATION_UID + "\",\"homeStationId\":\"" + STATION_UID
                        + "\",\"clusterId\":null,\"count\":4}",
                Row.class);

        assertEquals(0, row.stationId());
        assertNull(row.homeStationId());
        assertEquals(4, row.count());
    }

    @Test
    void aPartnerMapperWritesIdsAsTheyAre() {
        JsonMapper partner = JsonMapper.builder()
                .addModule(PublicIdModule.forPartnerResponses())
                .build();

        assertEquals(
                "{\"stationId\":7,\"homeStationId\":null,\"clusterId\":3,\"count\":1}",
                partner.writeValueAsString(new Row(7, null, 3, 1)));
    }
}
