package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.xray.entities.Subsegment;
import com.google.gson.Gson;
import com.syndicate.deployment.annotations.LambdaUrlConfig;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task09.request.ErrorMessage;
import com.task09.request.WeatherMessage;
import org.json.*;
import com.amazonaws.xray.AWSXRay;

import java.util.*;

@LambdaHandler(lambdaName = "processor",
        roleName = "processor-role",
        tracingMode = TracingMode.Active
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "region", value = "${region}"),
        @EnvironmentVariable(key = "target_table", value = "${target_table}")
})

public class Processor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final int SC_OK = 200;
    private static final int SC_BAD_REQUEST = 400;
    private final Gson gson = new Gson();
    private AmazonDynamoDB amazonDynamoDB;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, Context context) {
        Subsegment subsegment = AWSXRay.beginSubsegment("handleRequest");
        try {
            context.getLogger().log(apiGatewayProxyRequestEvent.toString());
            this.initDynamoDbClient();

            JSONObject jsonObj = new JSONObject();
            String weatherJsonStr = new WeatherMessage().getWeather();
            JSONObject weatherJsonObj = new JSONObject(weatherJsonStr);
            jsonObj.put("forecast", weatherJsonObj);
            persistData(jsonObj);
            try {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(SC_OK)
                        .withBody(new WeatherMessage().getWeather());
            } catch (IllegalArgumentException exception) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(SC_BAD_REQUEST)
                        .withBody(gson.toJson(new ErrorMessage(exception.getMessage())));
            }
        } finally {
            AWSXRay.endSegment();
        }
    }

    private Map<String, AttributeValue> persistData(JSONObject obj) throws ConditionalCheckFailedException {
        JSONObject forecast = obj.getJSONObject("forecast");
        JSONObject hourly = forecast.getJSONObject("hourly");
        JSONObject hourly_units = forecast.getJSONObject("hourly_units");

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("id", new AttributeValue(UUID.randomUUID().toString()));

        Map<String, AttributeValue> forecastItem = new HashMap<>();
        forecastItem.put("elevation", new AttributeValue().withN(String.valueOf(forecast.getDouble("elevation"))));
        forecastItem.put("generationtime_ms", new AttributeValue().withN(String.valueOf(forecast.getDouble("generationtime_ms"))));
        forecastItem.put("latitude", new AttributeValue().withN(String.valueOf(forecast.getDouble("latitude"))));
        forecastItem.put("longitude", new AttributeValue().withN(String.valueOf(forecast.getDouble("longitude"))));
        forecastItem.put("timezone", new AttributeValue(forecast.getString("timezone")));
        forecastItem.put("timezone_abbreviation", new AttributeValue(forecast.getString("timezone_abbreviation")));
        forecastItem.put("utc_offset_seconds", new AttributeValue().withN(String.valueOf(forecast.getDouble("utc_offset_seconds"))));

        JSONArray temperature_2m = hourly.getJSONArray("temperature_2m");
        List<AttributeValue> temp2mList = new ArrayList<>();
        for (int i = 0; i < temperature_2m.length(); i++) {
            temp2mList.add(new AttributeValue().withN(String.valueOf(temperature_2m.getDouble(i))));
        }

        JSONArray time = hourly.getJSONArray("time");
        List<AttributeValue> timeList = new ArrayList<>();
        for (int i = 0; i < time.length(); i++) {
            timeList.add(new AttributeValue().withS(time.getString(i)));
        }

        Map<String, AttributeValue> hourlyItem = new HashMap<>();
        hourlyItem.put("temperature_2m", new AttributeValue().withL(temp2mList));
        hourlyItem.put("time", new AttributeValue().withL(timeList));

        forecastItem.put("hourly", new AttributeValue().withM(hourlyItem));

        Map<String, AttributeValue> hourlyUnitsItem = new HashMap<>();
        hourlyUnitsItem.put("temperature_2m", new AttributeValue(hourly_units.getString("temperature_2m")));
        hourlyUnitsItem.put("time", new AttributeValue(hourly_units.getString("time")));

        forecastItem.put("hourly_units", new AttributeValue().withM(hourlyUnitsItem));

        item.put("forecast", new AttributeValue().withM(forecastItem));

        amazonDynamoDB.putItem(System.getenv("target_table"), item);
        return item;
    }

    private static List<String> toList(JSONArray jsonArray) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            list.add(jsonArray.getString(i));
        }
        return list;

    }

    private void initDynamoDbClient() {
        this.amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withRegion(System.getenv("region"))
                .build();
    }
}
