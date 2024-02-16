package com.task08.response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class WeatherMessage {
    private final String message;
    public WeatherMessage(String message) {
        this.message = message;
    }

}