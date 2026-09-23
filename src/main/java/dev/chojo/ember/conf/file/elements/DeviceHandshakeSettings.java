/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

/**
 * How hard the device handshake is throttled per network address.
 *
 * <p>Only the address-keyed limits are here, and they are here because an address is a poor way to
 * tell people apart: an office, a school or a scout hut reaches the internet as one, so a limit
 * meant to stop one machine grinding lands on everybody behind the router instead. An installation
 * that knows how many people sit behind its address can say so.
 *
 * <p>What is deliberately not configurable: the per-account limit on raising requests, and the two
 * limits on the approval screen. Those are what stops somebody guessing a code, and an operator who
 * widened them would not be able to tell what they had opened. The per-address numbers can be
 * generous without costing anything, because naming an account and matching a number are what
 * actually guard the flow.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class DeviceHandshakeSettings {
    private int requestBurst = 60;
    private int requestsPerMinute = 4;
    private int pollBurst = 600;
    private int pollsPerMinute = 600;
    private int enrolmentBurst = 40;
    private int enrolmentsPerMinute = 40;

    /**
     * @return how many requests one address may raise back to back before the refill decides the
     * pace. The default holds an office of sixty people each asking once.
     */
    public int requestBurst() {
        return requestBurst;
    }

    /**
     * @return how many requests one address gets back per minute once the burst is spent.
     */
    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    /**
     * @return how many polls one address may make back to back. A waiting device asks about
     * twenty-four times a minute, so the default carries roughly twenty-five of them.
     */
    public int pollBurst() {
        return pollBurst;
    }

    /**
     * @return how many polls one address gets back per minute. This is a backstop and not the real
     * limit: polling is bucketed per poll secret, which no other device shares.
     */
    public int pollsPerMinute() {
        return pollsPerMinute;
    }

    /**
     * @return how many credential ceremonies one address may open back to back, which matters on an
     * afternoon spent handing out devices.
     */
    public int enrolmentBurst() {
        return enrolmentBurst;
    }

    /**
     * @return how many enrolment ceremonies one address gets back per minute.
     */
    public int enrolmentsPerMinute() {
        return enrolmentsPerMinute;
    }

    @Override
    public String toString() {
        return "DeviceHandshakeSettings{requestBurst=" + requestBurst
                + ", requestsPerMinute=" + requestsPerMinute
                + ", pollBurst=" + pollBurst
                + ", pollsPerMinute=" + pollsPerMinute
                + ", enrolmentBurst=" + enrolmentBurst
                + ", enrolmentsPerMinute=" + enrolmentsPerMinute + '}';
    }
}
