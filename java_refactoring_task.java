package com.example.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
public class TransactionController {

  @PostMapping(value = "/get_transaction_data", consumes = "application/json", produces = "application/json")
  public ResponseEntity<?> getTransactionData(@RequestBody String rawBody) {
    ObjectMapper om = new ObjectMapper();
    Map<String, Object> data;

    try {
      data = om.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      Map<String, Object> err = new HashMap<>();
      err.put("error", "Invalid JSON");
      err.put("details", e.getMessage());
      return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
    }

    if (data == null || !data.containsKey("bank")) {
      return new ResponseEntity<>(Map.of("error", "Correct bank id should be set"), HttpStatus.BAD_REQUEST);
    }
    if (!data.containsKey("transaction_id")) {
      return new ResponseEntity<>(Map.of("error", "Transaction ID should be set"), HttpStatus.BAD_REQUEST);
    }

    String internalTxId;
    try {
      Object txIdObj = data.get("transaction_id");
      internalTxId = txIdObj == null ? "" : String.valueOf(txIdObj);
    } catch (Exception e) {
      return new ResponseEntity<>(Map.of("error", "Transaction ID should be set"), HttpStatus.BAD_REQUEST);
    }

    String bank = String.valueOf(data.get("bank"));
    if (!(bank.equals("bank_a") || bank.equals("bank_b"))) {
      return new ResponseEntity<>(Map.of("error", "Correct bank id should be set"), HttpStatus.BAD_REQUEST);
    }

    Map<String, Object> result = new HashMap<>();
    RestTemplate http = new RestTemplate();

    try {
      if (bank.equals("bank_a")) {
        // call: http://api1mock:8080/transactions/{id}
        String url = "http://api1mock:8080/transactions/" + internalTxId;
        String resp = http.getForObject(url, String.class);

        // expected: { "id": "abc-123", "state": "COMPLETED", "value": 10.50 }
        Map<String, Object> body = om.readValue(resp, new TypeReference<Map<String, Object>>() {});

        String externalTxId = String.valueOf(body.get("id"));
        String status = body.get("state") == null ? "PENDING" : String.valueOf(body.get("state"));
        String value = String.valueOf(body.get("value"));

        Map<String, Object> amount = new HashMap<>();
        amount.put("value", value);
        amount.put("currency", "EUR");

        result.put("id", externalTxId);
        result.put("status", status);
        result.put("amount", amount);

      } else if (bank.equals("bank_b")) {
        // call: http://api2mock:8080/get_transaction?id={id}
        String url2 = "http://api2mock:8080/get_transaction?id=" + internalTxId;
        String resp2 = http.getForObject(url2, String.class);

        // expected: { "transactionId": "xyz-789", "status": "SUCCESS", "amount": 9.00, "currency": "USD" }
        Map<String, Object> body2 = om.readValue(resp2, new TypeReference<>() {});

        String externalTxId2 = String.valueOf(body2.get("transactionId"));
        String status2 = String.valueOf(body2.getOrDefault("status", "PENDING"));
        String value2 = String.valueOf(body2.get("amount"));
        String currency2 = String.valueOf(body2.getOrDefault("currency", "USD"));

        Map<String, Object> amount2 = new HashMap<>();

        if ("EUR".equalsIgnoreCase(currency2)) {
          // already EUR, nothing to do
          amount2.put("value", value2);
        } else {
          // convert from USD to EUR
          BigDecimal valueDecimal = new BigDecimal(value2);
          BigDecimal convertedToEur = valueDecimal.multiply(new BigDecimal("0.90"));
          amount2.put("value", convertedToEur);
        }
        amount2.put("currency", "EUR");

        result.put("id", externalTxId2);
        result.put("status", status2.toUpperCase(Locale.ROOT));
        result.put("amount", amount2);
      }

      // output: {"id": "xyz-789", "status": "SUCCESS", "amount": {"value": 9.00, "currency": "USD"}}
      return ResponseEntity.ok(result);

    } catch (Exception ex) {
      Map<String, Object> err = new HashMap<>();
      err.put("error", "Upstream bank error");
      err.put("details", ex.getMessage());
      return new ResponseEntity<>(err, HttpStatus.BAD_GATEWAY);
    }
  }
}
