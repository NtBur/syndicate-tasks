package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.CloudFrontEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.syndicate.deployment.annotations.LambdaUrlConfig;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import software.amazon.awssdk.core.Response;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;



import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@LambdaHandler(lambdaName = "api_handler",
	roleName = "api_handler-role"
)
@LambdaUrlConfig(
		authType= AuthType.NONE,
		invokeMode= InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "tables_table", value = "${tables_table}"),
		@EnvironmentVariable(key = "reservations_table", value = "${reservations_table}"),
		@EnvironmentVariable(key = "booking_userpool", value = "${booking_userpool}")
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	private CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
			.region(Region.of(System.getenv("region")))
			.build();
	CognitoClient client = new CognitoClient();
	private String userPoolId = client.getUserPoolId(cognitoClient, System.getenv("booking_userpool"));
	private String clientId = client.getClientId(cognitoClient, userPoolId);

		public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
			if(clientId!=null){
			String path = input.getPath();

			switch (path) {
				case "/signup":
					return handleSignup(input);
				case "/signin":
					return handleSignin(input);
				case "/tables":
					String httpMethod = input.getHttpMethod();
					if ("GET".equalsIgnoreCase(httpMethod)) {
						return handleGetTables(input);
					} else if ("POST".equalsIgnoreCase(httpMethod)) {
						//return handlePostTables(input);
					}
					break;
				case "/tables/{tableId}":
					//	return handleGetTable(input);
			}
			}
			return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid request");
		}

		private APIGatewayProxyResponseEvent handleSignup(APIGatewayProxyRequestEvent inputBody) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Map<String, String> input = gson.fromJson(inputBody.getBody(), new TypeToken<Map<String, String>>() {}.getType());

					AdminCreateUserRequest adminCreateUserRequest = AdminCreateUserRequest.builder()
							.userPoolId(userPoolId)
							.messageAction("SUPPRESS")
							.username(input.get("email"))
							.temporaryPassword(input.get("password"))
							.userAttributes(
									AttributeType.builder().name("name").value(input.get("firstName")).build(),
									AttributeType.builder().name("family_name").value(input.get("lastName")).build(),
									AttributeType.builder().name("email").value(input.get("email")).build()
							)
							.build();

					try {
						AdminCreateUserResponse adminCreateUserResponse = cognitoClient.adminCreateUser(adminCreateUserRequest);
						return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("User registered successfully");
					} catch (UsernameExistsException e) {
						return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Username already exists");
					}
				}



		private APIGatewayProxyResponseEvent handleSignin(APIGatewayProxyRequestEvent inputBody) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Map<String, String> input = gson.fromJson(inputBody.getBody(), new TypeToken<Map<String, String>>() {}.getType());
			InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
					.authFlow(AuthFlowType.USER_PASSWORD_AUTH)
					.clientId(clientId)
					.authParameters(buildAuthParameters(input.get("email"), input.get("password")))
					.build();

			try {
				InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
				String accessToken = authResponse.authenticationResult().idToken();
				return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"accessToken\":\"" + accessToken + "\"}");
			} catch (Exception e) {
				return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Auth failed: " + e.getMessage());
			}
		}

	private Map<String,String> buildAuthParameters(String username, String password) {
		Map<String,String> authParameters = new HashMap<>();
		authParameters.put("USERNAME", username);
		authParameters.put("PASSWORD", password);
		return authParameters;
	}


		private APIGatewayProxyResponseEvent handleGetTables(APIGatewayProxyRequestEvent input) {
			List<Map<String, Object>> tables = new ArrayList<>();

			Map<String, Object> table1 = new HashMap<>();
			table1.put("id", 1);
			table1.put("number", 10);
			table1.put("places", 4);
			table1.put("isVip", false);
			table1.put("minOrder", 20);
			tables.add(table1);

			Map<String, Object> table2 = new HashMap<>();
			table2.put("id", 2);
			table2.put("number", 20);
			table2.put("places", 2);
			table2.put("isVip", true);
			tables.add(table2);

			Map<String, List<Map<String, Object>>> response = new HashMap<>();
			response.put("tables", tables);

			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String json = gson.toJson(response);
			return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(json);
		}

}