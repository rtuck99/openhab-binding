package com.qubular.glowmarkt;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface GlowmarktService {
    GlowmarktSession authenticate(GlowmarktSettings settings, String username, String password) throws IOException, AuthenticationFailedException;

    List<VirtualEntity> getVirtualEntities(GlowmarktSession session,
                                           GlowmarktSettings settings) throws IOException, AuthenticationFailedException;

    VirtualEntity getVirtualEntity(GlowmarktSession session,
                                   GlowmarktSettings settings,
                                   String virtualEntityId) throws IOException, AuthenticationFailedException;

    /**
     * Finally, be aware that when the aggregation periods of P1W and P1M are used, the start date of the query
     * (“to” date) should be set to the beginning of the week (Monday) or month respectively (1st day).
     * Depending on the aggregation period, there is a limit to the volume of data that can be
     * requested per query as follows
     *  <table>
     *      <tr><th>Period Parameter</th><th>Description</th><th>Limit in days</th></tr>
     *      <tr>PT30M<td><td>30-minute level</td><td>10 days</td></tr>
     *      <tr><td>PT1H</td><td>1-hour level</td><td>31 days</td></tr>
     *      <tr><td>P1D</td><td>1-day level</td><td>31 days</td></tr>
     *      <tr><td>P1W</td><td>1-week level</td><td>6 weeks</td></tr>
     *      <tr><td>P1M</td><td>1-month level</td><td>366 days</td></tr>
     *      <tr><td>P1Y</td><td>1-year level</td><td>366 days</td></tr>
     *  </table>
     * @return
     */
    List<ResourceData> getResourceReadings(GlowmarktSession session,
                                       GlowmarktSettings settings,
                                       String resourceID,
                                       Instant from,
                                       Instant to,
                                       AggregationPeriod period,
                                       AggregationFunction aggregationFunction) throws IOException, AuthenticationFailedException;

    TariffResponse getResourceTariff(GlowmarktSession session, GlowmarktSettings settings, String resourceId) throws IOException, AuthenticationFailedException;

    void catchup(GlowmarktSession session,
                 GlowmarktSettings settings,
                 String resourceId);

    Instant getFirstTime(GlowmarktSession session,
                         GlowmarktSettings settings,
                         String resourceId) throws AuthenticationFailedException, IOException;

    Instant getLastTime(GlowmarktSession session,
                         GlowmarktSettings settings,
                         String resourceId) throws AuthenticationFailedException, IOException;
}
