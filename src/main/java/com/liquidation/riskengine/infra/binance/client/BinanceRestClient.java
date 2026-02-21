package com.liquidation.riskengine.infra.binance.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquidation.riskengine.infra.binance.config.BinanceProperties;
import com.liquidation.riskengine.infra.binance.dto.FundingRateResponse;
import com.liquidation.riskengine.infra.binance.dto.OpenInterestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceRestClient {

    private final OkHttpClient okHttpClient;
    private final BinanceProperties properties;
    private final ObjectMapper objectMapper;

    public Optional<FundingRateResponse> getLatestFundingRate(String symbol) {
        String url = properties.getRestBaseUrl()
                + "/fapi/v1/fundingRate?symbol=" + symbol.toUpperCase() + "&limit=1";

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[Binance REST] FundingRate 요청 실패: symbol={}, code={}", symbol, response.code());
                return Optional.empty();
            }

            ResponseBody body = response.body();
            if (body == null) return Optional.empty();

            FundingRateResponse[] arr = objectMapper.readValue(body.string(), FundingRateResponse[].class);
            if (arr.length == 0) return Optional.empty();

            log.debug("[Binance REST] FundingRate 수신: symbol={}, rate={}", symbol, arr[0].getFundingRate());
            return Optional.of(arr[0]);

        } catch (Exception e) {
            log.error("[Binance REST] FundingRate 요청 예외: symbol={}", symbol, e);
            return Optional.empty();
        }
    }

    public Optional<OpenInterestResponse> getOpenInterest(String symbol) {
        String url = properties.getRestBaseUrl()
                + "/fapi/v1/openInterest?symbol=" + symbol.toUpperCase();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[Binance REST] OI 요청 실패: symbol={}, code={}", symbol, response.code());
                return Optional.empty();
            }

            ResponseBody body = response.body();
            if (body == null) return Optional.empty();

            OpenInterestResponse oi = objectMapper.readValue(body.string(), OpenInterestResponse.class);
            log.info("[Binance REST] OI 수신: symbol={}, openInterest={}", symbol, oi.getOpenInterest());
            return Optional.of(oi);

        } catch (Exception e) {
            log.error("[Binance REST] OI 요청 예외: symbol={}", symbol, e);
            return Optional.empty();
        }
    }
}
