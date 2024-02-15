package com.task08.response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class WeatherMessage {
    private final String BASE_URL = "http://api.open-meteo.com/v1/forecast?latitude=49.08&longitude=34.11&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";


    public String getWeather() {

        StringBuffer response = null;
        try {
            URL url = new URL(BASE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            response = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                response.append(line);
            }
            rd.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        assert response != null;
        return response.toString();
    }
}