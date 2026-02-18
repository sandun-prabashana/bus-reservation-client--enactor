/*
 * @created 18/02/2026 - 12:17 PM
 * @project bus-reservation-client
 * @author sandun_p
 */

package com.company.bus.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class BookingClient {

    private static final ObjectMapper mapper    = new ObjectMapper();
    private static final String       BASE_URL  = "http://localhost:8080";

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter number of users to simulate: ");
        int users = scanner.nextInt();

        System.out.print("Enter origin (A/B/C/D): ");
        String origin = scanner.next().toUpperCase().trim();

        System.out.print("Enter destination (A/B/C/D): ");
        String destination = scanner.next().toUpperCase().trim();

        System.out.print("Enter passengers per booking: ");
        int passengers = scanner.nextInt();

        System.out.println("\nStarting booking simulation with " + users + " concurrent users...\n");

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(users, 20));

        for (int i = 1; i <= users; i++) {
            int userId = i;
            executor.submit(() -> simulateUser(userId, origin, destination, passengers));
        }

        executor.shutdown();
    }


    private static void simulateUser(int userId, String origin, String destination, int passengers) {

        try {
            Map<String, Object> availRequest = new HashMap<>();
            availRequest.put("origin",      origin);
            availRequest.put("destination", destination);
            availRequest.put("passengers",  passengers);

            HttpResult availResult = post(BASE_URL + "/availability", availRequest);

            System.out.printf("User %-3d [availability] HTTP %d → %s%n",
                    userId, availResult.statusCode, availResult.body);

            if (availResult.statusCode != 200) {
                System.out.printf("User %-3d → Availability check failed, skipping reservation.%n", userId);
                return;
            }

            Map<String, Object> availResponse = mapper.readValue(availResult.body, Map.class);
            boolean available = (boolean) availResponse.get("available");

            if (!available) {
                System.out.printf("User %-3d → No seats available, skipping reservation.%n", userId);
                return;
            }

            int quotedTotal = (int) availResponse.get("totalPrice");

            Map<String, Object> reserveRequest = new HashMap<>();
            reserveRequest.put("origin",      origin);
            reserveRequest.put("destination", destination);
            reserveRequest.put("passengers",  passengers);
            reserveRequest.put("amountPaid",  quotedTotal);

            HttpResult reserveResult = post(BASE_URL + "/reserve", reserveRequest);

            System.out.printf("User %-3d [reserve]      HTTP %d → %s%n",
                    userId, reserveResult.statusCode, reserveResult.body);

        } catch (Exception e) {
            System.out.printf("User %-3d → ERROR: %s%n", userId, e.getMessage());
        }
    }


    private static HttpResult post(String urlString, Map<String, Object> body) throws Exception {

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);

        byte[] json = mapper.writeValueAsBytes(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json);
        }

        int statusCode = conn.getResponseCode();
        InputStream responseStream = (statusCode >= 200 && statusCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        return new HttpResult(statusCode, sb.toString());
    }


    private static class HttpResult {
        final int    statusCode;
        final String body;

        HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body       = body;
        }
    }
}