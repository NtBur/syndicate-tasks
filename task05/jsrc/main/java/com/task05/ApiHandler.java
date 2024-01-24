package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.LambdaUrlConfig;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task05.request.EventRequest;

import java.text.SimpleDateFormat;
import java.util.*;

@LambdaHandler(lambdaName = "api_handler",
	roleName = "api_handler-role"
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "target_table", value = "${target_table}")
})
public class ApiHandler implements RequestHandler<EventRequest, Map<String, Object>> {
	private AmazonDynamoDB amazonDynamoDB;

	public Map<String, Object> handleRequest(EventRequest request, Context context) {
		this.initDynamoDbClient();

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("statusCode", 201);
		response.put("body", persistData(request));
		return response;
	}

	private Map<String, AttributeValue> persistData(EventRequest request) throws ConditionalCheckFailedException {

		Map<String, AttributeValue> attributesMap = new HashMap<>();

		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));

		attributesMap.put("id", new AttributeValue(String.valueOf(UUID.randomUUID())));
		attributesMap.put("principalId", new AttributeValue(String.valueOf(request.getPrincipalId())));
		attributesMap.put("createdAt", new AttributeValue(format.toString()));
		attributesMap.put("body", new AttributeValue(String.valueOf(request.getContent())));

		amazonDynamoDB.putItem(System.getenv("target_table"), attributesMap);
		return attributesMap;

	}

	private void initDynamoDbClient() {
		this.amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
				.withRegion(System.getenv("region"))
				.build();
	}
}
