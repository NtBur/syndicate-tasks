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

import java.time.Instant;
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
		response.put("event", persistData(request));
		response.put("statusCode", 201);
		return response;
	}

	private Map<String, AttributeValue> persistData(EventRequest request) throws ConditionalCheckFailedException {

		Map<String, AttributeValue> attributesMap = new HashMap<>();

		attributesMap.put("id", new AttributeValue(String.valueOf(UUID.randomUUID())));
		attributesMap.put("principalId", new AttributeValue().withN(String.valueOf(request.getPrincipalId())));
		attributesMap.put("createdAt", new AttributeValue(String.valueOf(Instant.now())));
		Map<String, AttributeValue>  body = new HashMap<>();
		for(Map.Entry<String, String> entry : request.getContent().entrySet()){
			body.put(entry.getKey(), new AttributeValue(entry.getValue()));
		}
		attributesMap.put("body", new AttributeValue().withM(body));

		amazonDynamoDB.putItem(System.getenv("target_table"), attributesMap);
		return attributesMap;

	}

	private void initDynamoDbClient() {
		this.amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
				.withRegion(System.getenv("region"))
				.build();
	}
}
