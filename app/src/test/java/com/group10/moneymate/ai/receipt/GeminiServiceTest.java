package com.group10.moneymate.ai.receipt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

public class GeminiServiceTest {

    @Test
    public void parseReceipt_shouldReturnSuccessForValidGeminiJson() {
        GeminiService service = new GeminiService("test-key", new FakeTransport(
                200,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"merchant\\\":\\\"CIRCLE K\\\",\\\"date\\\":\\\"09/04/2026\\\",\\\"total\\\":55000,\\\"category_hint\\\":\\\"Mua sắm\\\",\\\"note_hint\\\":\\\"CIRCLE K\\\",\\\"items\\\":[{\\\"name\\\":\\\"Ca phe sua\\\",\\\"price\\\":25000,\\\"quantity\\\":1,\\\"category\\\":\\\"Ăn uống\\\"}],\\\"total_candidates\\\":[{\\\"label\\\":\\\"Tong cong\\\",\\\"amount\\\":55000,\\\"line_order\\\":8}],\\\"confidence\\\":86}\"}]}}]}"
        ));

        GeminiService.GeminiResult result = service.parseReceipt(
                new byte[]{1, 2, 3},
                "image/jpeg"
        );

        assertTrue(result instanceof GeminiService.GeminiResult.Success);
        GeminiService.ParsedReceipt parsedReceipt =
                ((GeminiService.GeminiResult.Success) result).getParsedReceipt();
        assertEquals("CIRCLE K", parsedReceipt.getMerchant());
        assertEquals("09/04/2026", parsedReceipt.getDate());
        assertEquals(55000d, parsedReceipt.getTotal(), 0.0d);
        assertEquals("Mua sắm", parsedReceipt.getCategoryHint());
        assertEquals("CIRCLE K", parsedReceipt.getNoteHint());
        assertEquals(1, parsedReceipt.getItems().size());
        assertEquals(1, parsedReceipt.getTotalCandidates().size());
    }

    @Test
    public void parseReceipt_shouldReturnErrorWhenCriticalFieldsAreMissing() {
        GeminiService service = new GeminiService("test-key", new FakeTransport(
                200,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"merchant\\\":\\\"\\\",\\\"date\\\":\\\"\\\",\\\"total\\\":0,\\\"items\\\":[],\\\"confidence\\\":20}\"}]}}]}"
        ));

        GeminiService.GeminiResult result = service.parseReceipt(
                new byte[]{1, 2, 3},
                "image/jpeg"
        );

        assertTrue(result instanceof GeminiService.GeminiResult.Error);
        assertEquals(
                "missing_amount",
                ((GeminiService.GeminiResult.Error) result).getErrorCode()
        );
    }

    @Test
    public void parseReceipt_shouldReturnHttpErrorForServerFailure() {
        GeminiService service = new GeminiService("test-key", new FakeTransport(503, "{\"error\":{}}"));

        GeminiService.GeminiResult result = service.parseReceipt(
                new byte[]{1, 2, 3},
                "image/jpeg"
        );

        assertTrue(result instanceof GeminiService.GeminiResult.Error);
        assertEquals(
                "http_503",
                ((GeminiService.GeminiResult.Error) result).getErrorCode()
        );
    }

    private static final class FakeTransport implements GeminiService.GeminiTransport {
        private final int statusCode;
        private final String body;

        private FakeTransport(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public GeminiService.TransportResponse generateContent(String apiKey, String requestBody) throws IOException {
            return new GeminiService.TransportResponse(statusCode, body, -1L);
        }
    }
}
