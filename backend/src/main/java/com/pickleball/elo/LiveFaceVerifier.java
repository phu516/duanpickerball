package com.pickleball.elo;

import com.pickleball.common.exception.BusinessException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class LiveFaceVerifier {
    private final RestClient client;

    LiveFaceVerifier(@Value("${FACE_SERVICE_URL:http://localhost:8001}") String url) {
        client = RestClient.create(url);
    }

    double[] consume(String purpose, String proof) {
        if (proof == null || !proof.matches("[A-Za-z0-9_-]{43}")) throw invalidProof();
        try {
            return client.post().uri("/consume/{purpose}", purpose)
                    .body(Map.of("proof", proof)).retrieve().body(double[].class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) throw invalidProof();
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private BusinessException invalidProof() {
        return new BusinessException(HttpStatus.FORBIDDEN, "LIVE_FACE_REQUIRED", "Vui lòng quét lại khuôn mặt.");
    }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "FACE_SERVICE_UNAVAILABLE", "Dịch vụ quét mặt chưa sẵn sàng.");
    }
}
