/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The transport as a whole: which side a partner is sent to, how a partner on this instance is
 * answered, and how a partner on another one is asked.
 */
class FederationTransportTest {

    record Answer(int id, String name, int stationId) {}

    record NameOnly(String name) {}

    record Body(String text) {}

    record OtherBody(String text, int extra) {}

    private static final FederationEndpoint GET_ONE =
            FederationEndpoint.get(FederationSurface.NEWS_SHARE, "/remote/test/{id}", Answer.class);
    private static final FederationEndpoint GET_MANY =
            FederationEndpoint.getList(FederationSurface.NEWS_SHARE, "/remote/test", Answer.class);
    private static final FederationEndpoint POST_ONE =
            FederationEndpoint.post(FederationSurface.NEWS_SHARE, "/remote/test/{id}", Body.class, Answer.class);
    private static final FederationEndpoint POST_MANY =
            FederationEndpoint.postList(FederationSurface.NEWS_SHARE, "/remote/test/many", Body.class, Answer.class);
    private static final FederationEndpoint PUT_ONE =
            FederationEndpoint.put(FederationSurface.NEWS_SHARE, "/remote/test/{id}", Body.class, Answer.class);
    private static final FederationEndpoint PUT_NOTHING =
            FederationEndpoint.put(FederationSurface.NEWS_SHARE, "/remote/test/{id}/quiet", Body.class, Void.class);
    private static final FederationEndpoint DELETE =
            FederationEndpoint.delete(FederationSurface.NEWS_SHARE, "/remote/test/{id}", Void.class, Void.class);
    private static final FederationEndpoint DELETE_WITH_BODY =
            FederationEndpoint.delete(FederationSurface.NEWS_SHARE, "/remote/test/{id}/body", Body.class, Void.class);
    private static final FederationEndpoint POST_NOTHING =
            FederationEndpoint.post(FederationSurface.NEWS_SHARE, "/remote/test/hook", Body.class, Void.class);

    private final UUID askingUid = UUID.randomUUID();
    private final UUID servingUid = UUID.randomUUID();
    private FederationHttpClient httpClient;
    private FederationWebhookService webhooks;
    private FederationRepository federationRepo;
    private StationRepository stationRepo;
    private RoutingFederationTransport transport;
    private final AtomicReference<ServingPartner> lastServed = new AtomicReference<>();
    private final AtomicReference<Object> lastBody = new AtomicReference<>();

    @BeforeEach
    void wire() {
        httpClient = mock(FederationHttpClient.class);
        webhooks = mock(FederationWebhookService.class);
        federationRepo = mock(FederationRepository.class);
        stationRepo = mock(StationRepository.class);
        when(stationRepo.resolveUid(1)).thenReturn(askingUid);
        when(stationRepo.findByUid(servingUid)).thenReturn(Optional.of(mock(Station.class)));
        var registry = new FederationEndpoints(Set.of(endpoints -> {
            endpoints.serve(GET_ONE, (partner, params, body) -> {
                lastServed.set(partner);
                return new Answer(params.integer("id"), "one", 5);
            });
            endpoints.serve(GET_MANY, (partner, params, body) -> List.of(new Answer(1, "a", 5), new Answer(2, "b", 5)));
            endpoints.serve(POST_ONE, (partner, params, body) -> {
                lastBody.set(body);
                return new Answer(params.integer("id"), ((Body) body).text(), 5);
            });
            endpoints.serve(POST_MANY, (partner, params, body) -> List.of(new Answer(3, ((Body) body).text(), 5)));
            endpoints.serve(PUT_NOTHING, (partner, params, body) -> null);
            endpoints.serve(POST_NOTHING, (partner, params, body) -> {
                throw Refusal.FEDERATION_PARTNER_DID_NOT_ANSWER.raise();
            });
        }));
        var local = new LocalFederationTransport(() -> registry, federationRepo, stationRepo);
        transport = new RoutingFederationTransport(new HttpFederationTransport(httpClient, webhooks), local);
    }

    private FederationPartner partner(String remoteHost, FederationStatus status) {
        return new FederationPartner(
                10, 1, servingUid, null, null, null, status, null, Instant.now(), Instant.now(), remoteHost, "X");
    }

    private FederationPartner servingRow(FederationStatus status) {
        return new FederationPartner(
                20, 2, askingUid, null, null, null, status, null, Instant.now(), Instant.now(), null, "Y");
    }

    private void servingRowIs(FederationStatus status) {
        var station = stationWithId(2);
        when(stationRepo.findByUid(servingUid)).thenReturn(Optional.of(station));
        when(federationRepo.findPartnerByStationAndRemoteUid(2, askingUid)).thenReturn(Optional.of(servingRow(status)));
    }

    private static Station stationWithId(int id) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(id);
        return station;
    }

    @Test
    void aPartnerHereIsAnsweredByTheServingFunctionWithItsOwnRow() {
        servingRowIs(FederationStatus.ACTIVE);
        var local = partner(null, FederationStatus.ACTIVE);

        var answer = transport.get(local, GET_ONE.at(4), Answer.class);

        assertEquals(new Answer(4, "one", 5), answer);
        assertEquals(20, lastServed.get().partnerId());
        assertEquals(2, lastServed.get().servingStationId());
        assertEquals(askingUid, lastServed.get().askingStationUid());
        verify(httpClient, never()).canSign(anyInt());
    }

    @Test
    void aTypeOfTheCallersOwnIsConvertedAsTheWireWouldRead() {
        servingRowIs(FederationStatus.ACTIVE);
        var local = partner(null, FederationStatus.ACTIVE);

        assertEquals(new NameOnly("one"), transport.get(local, GET_ONE.at(4), NameOnly.class));
        assertEquals(
                List.of(new NameOnly("a"), new NameOnly("b")), transport.getList(local, GET_MANY.at(), NameOnly.class));
        assertEquals(
                List.of(new Answer(1, "a", 5), new Answer(2, "b", 5)),
                transport.getList(local, GET_MANY.at(), Answer.class));
    }

    @Test
    void aBodyOfAnotherTypeIsConvertedToTheDeclaredOne() {
        servingRowIs(FederationStatus.ACTIVE);
        var local = partner(null, FederationStatus.ACTIVE);

        var sameType = new Body("same");
        transport.send(local, POST_ONE.at(1), sameType, Answer.class);
        assertSame(sameType, lastBody.get());

        var answer = transport.send(local, POST_ONE.at(2), new OtherBody("other", 3), Answer.class);
        assertEquals(new Answer(2, "other", 5), answer);
        assertEquals(new Body("other"), lastBody.get());

        assertEquals(
                List.of(new Answer(3, "many", 5)),
                transport.sendList(local, POST_MANY.at(), new Body("many"), Answer.class));
        transport.deliver(local, PUT_NOTHING.at(1), new Body("x"));
    }

    @Test
    void aPartnershipTheOtherSideNoLongerHoldsIsRefused() {
        servingRowIs(FederationStatus.SUSPENDED);
        var local = partner(null, FederationStatus.ACTIVE);

        var refused = assertThrows(RefusalResponse.class, () -> transport.get(local, GET_ONE.at(1), Answer.class));
        assertEquals(Refusal.FEDERATION_PARTNERSHIP_NOT_ACTIVE_THERE, refused.refusal());

        when(stationRepo.findByUid(servingUid)).thenReturn(Optional.empty());
        assertThrows(RefusalResponse.class, () -> transport.getList(local, GET_MANY.at(), Answer.class));
    }

    @Test
    void aRefusalOfTheServingFunctionPassesThroughAndANotificationSwallowsIt() {
        servingRowIs(FederationStatus.ACTIVE);
        var local = partner(null, FederationStatus.ACTIVE);

        assertThrows(RefusalResponse.class, () -> transport.deliver(local, POST_NOTHING.at(), new Body("x")));
        transport.notify(local, POST_NOTHING.at(), new Body("x"));
        transport.notify(partner(null, FederationStatus.SUSPENDED), POST_NOTHING.at(), new Body("x"));
    }

    @Test
    void aCallerReadingMoreThanTheContractCoversFailsWhereverThePartnerIs() {
        record Wider(int id, String secret) {}
        assertThrows(
                IllegalArgumentException.class,
                () -> transport.get(partner(null, FederationStatus.ACTIVE), GET_ONE.at(1), Wider.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> transport.getList(partner("https://x", FederationStatus.ACTIVE), GET_MANY.at(), Wider.class));
    }

    @Test
    void aPartnerElsewhereIsSentASignedRequestAsTheAskingStation() {
        var remote = partner("https://partner.example", FederationStatus.ACTIVE);
        when(httpClient.canSign(1)).thenReturn(true);
        var request = GET_ONE.at(1);
        when(httpClient.get("https://partner.example", request, servingUid, 1, Answer.class))
                .thenReturn(new Answer(1, "far", 0));
        when(httpClient.getList("https://partner.example", GET_MANY.at(), servingUid, 1, Answer.class))
                .thenReturn(List.of(new Answer(9, "far", 0)));

        assertEquals("far", transport.get(remote, request, Answer.class).name());
        assertEquals(1, transport.getList(remote, GET_MANY.at(), Answer.class).size());
        verify(federationRepo, never()).findPartnerByStationAndRemoteUid(anyInt(), any());
    }

    @Test
    void aPartnerElsewhereThatDoesNotAnswerIsRefusedOrEmpty() {
        var remote = partner("https://partner.example", FederationStatus.ACTIVE);
        when(httpClient.canSign(1)).thenReturn(true);

        var refused = assertThrows(RefusalResponse.class, () -> transport.get(remote, GET_ONE.at(1), Answer.class));
        assertEquals(Refusal.FEDERATION_PARTNER_DID_NOT_ANSWER, refused.refusal());
        assertThrows(RefusalResponse.class, () -> transport.send(remote, POST_ONE.at(1), new Body("x"), Answer.class));
        assertThrows(RefusalResponse.class, () -> transport.deliver(remote, PUT_NOTHING.at(1), new Body("x")));
        assertTrue(transport
                .sendList(remote, POST_MANY.at(), new Body("x"), Answer.class)
                .isEmpty());
    }

    @Test
    void aStationWithoutAKeyAsksNobodyElsewhere() {
        var remote = partner("https://partner.example", FederationStatus.ACTIVE);
        when(httpClient.canSign(1)).thenReturn(false);

        assertTrue(transport.getList(remote, GET_MANY.at(), Answer.class).isEmpty());
        assertTrue(transport
                .sendList(remote, POST_MANY.at(), new Body("x"), Answer.class)
                .isEmpty());
        assertThrows(RefusalResponse.class, () -> transport.get(remote, GET_ONE.at(1), Answer.class));
        verify(httpClient, never()).get(any(), any(), any(), anyInt(), eq(Answer.class));
    }

    @Test
    void everyVerbGoesOutAsDeclared() {
        var remote = partner("https://partner.example", FederationStatus.ACTIVE);
        String host = "https://partner.example";
        when(httpClient.canSign(1)).thenReturn(true);
        var body = new Body("x");
        when(httpClient.post(host, POST_ONE.at(1), body, servingUid, 1, Answer.class))
                .thenReturn(new Answer(1, "posted", 0));
        when(httpClient.put(host, PUT_ONE.at(1), body, servingUid, 1, Answer.class))
                .thenReturn(new Answer(1, "put", 0));
        when(httpClient.postList(host, POST_MANY.at(), body, servingUid, 1, Answer.class))
                .thenReturn(List.of(new Answer(1, "many", 0)));
        when(httpClient.put(host, PUT_NOTHING.at(1), body, servingUid, 1)).thenReturn(true);
        when(httpClient.delete(host, DELETE.at(1), servingUid, 1)).thenReturn(true);
        when(httpClient.delete(host, DELETE_WITH_BODY.at(1), body, servingUid, 1))
                .thenReturn(true);
        when(httpClient.post(host, POST_NOTHING.at(), body, servingUid, 1)).thenReturn(true);

        assertEquals(
                "posted",
                transport.send(remote, POST_ONE.at(1), body, Answer.class).name());
        assertEquals(
                "put", transport.send(remote, PUT_ONE.at(1), body, Answer.class).name());
        assertEquals(
                1,
                transport.sendList(remote, POST_MANY.at(), body, Answer.class).size());
        transport.deliver(remote, PUT_NOTHING.at(1), body);
        transport.deliver(remote, DELETE.at(1), null);
        transport.deliver(remote, DELETE_WITH_BODY.at(1), body);
        transport.deliver(remote, POST_NOTHING.at(), body);
        verify(httpClient).put(host, PUT_NOTHING.at(1), body, servingUid, 1);
        verify(httpClient).delete(host, DELETE.at(1), servingUid, 1);
        verify(httpClient).delete(host, DELETE_WITH_BODY.at(1), body, servingUid, 1);
        verify(httpClient).post(host, POST_NOTHING.at(), body, servingUid, 1);
        assertThrows(IllegalArgumentException.class, () -> transport.send(remote, DELETE.at(1), null, Answer.class));
        assertThrows(IllegalArgumentException.class, () -> transport.deliver(remote, GET_ONE.at(1), null));
    }

    @Test
    void aPushElsewhereGoesThroughTheWebhookService() {
        var remote = partner("https://partner.example", FederationStatus.ACTIVE);
        var body = new Body("x");
        transport.notify(remote, POST_NOTHING.at(), body);
        verify(webhooks).notifyPartner(10, POST_NOTHING.at(), body);
    }

    @Test
    void theRegistryHoldsOneFunctionPerEndpoint() {
        var registry = new FederationEndpoints(Set.of());
        assertThrows(IllegalStateException.class, () -> registry.handlerFor(GET_ONE));
        registry.serve(GET_ONE, (partner, params, body) -> null);
        assertTrue(registry.serves(GET_ONE));
        assertThrows(IllegalStateException.class, () -> registry.serve(GET_ONE, (partner, params, body) -> null));
    }
}
