package com.qubular.vicare;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class URIHelper {
    public static Map<String, String> getQueryParams(URI uri) {
        Map<String, String> queryParams = Arrays.stream(uri.getQuery().split("&"))
                .map(Pattern.compile("(.*?)=(.*)")::matcher)
                .filter(Matcher::matches)
                .collect(Collectors.toMap(m -> URLDecoder.decode(m.group(1), UTF_8),
                        m -> URLDecoder.decode(m.group(2), UTF_8)));
        return queryParams;
    }

    public static String generateQueryParamsForURI(Map<String, String> queryParams) {
        return queryParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), UTF_8).replaceAll("\\+","%20") +
                        "=" +
                        URLEncoder.encode(e.getValue(), UTF_8).replaceAll("\\+", "%20"))
                .collect(Collectors.joining("&"));
    }
}
