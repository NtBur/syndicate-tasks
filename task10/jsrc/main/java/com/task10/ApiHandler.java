package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.syndicate.deployment.annotations.LambdaUrlConfig;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;


import java.util.HashMap;
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
public class ApiHandler implements  RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
			.region(Region.of(System.getenv("region")))
			.build();
		public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Map<String, String> requestBody = gson.fromJson(event.getBody(), new TypeToken<Map<String, String>>() {}.getType());

			String firstName = requestBody.get("firstName");
			String lastName = requestBody.get("lastName");
			String email = requestBody.get("email");
			String password = requestBody.get("password");

			if ("/signup".equals(event.getResource()) && "POST".equals(event.getHttpMethod())) {
				AdminCreateUserRequest signUpRequest = AdminCreateUserRequest.builder()
						.userPoolId(System.getenv("booking_userpool"))
						.username(email)
						.userAttributes(
								AttributeType.builder().name("given_name").value(firstName).build(),
								AttributeType.builder().name("family_name").value(lastName).build(),
								AttributeType.builder().name("email").value(email).build()
						)

						.temporaryPassword("TempPassword1!")
						.build();

				try {
					AdminCreateUserResponse response = cognitoClient.adminCreateUser(signUpRequest);
					if(response.sdkHttpResponse().isSuccessful()) {
						// If user creation is successful, set the real (given) password
						AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
								.userPoolId(System.getenv("booking_userpool"))
								.username(email)
								.password(password)
								.permanent(true)
								.build();

						// Use the client to send the request
						AdminSetUserPasswordResponse setPasswordResponse = cognitoClient.adminSetUserPassword(setPasswordRequest);
						if (setPasswordResponse.sdkHttpResponse().isSuccessful()) {
							return new APIGatewayProxyResponseEvent().withStatusCode(200);
						}
					}
				} catch (Exception ex) {
					return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody(ex.getMessage());
				}
			} else if ("/signin".equals(event.getResource()) && "POST".equals(event.getHttpMethod())) {



				AdminInitiateAuthRequest signInRequest = AdminInitiateAuthRequest.builder()
						.authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
						.clientId(getClientId(cognitoClient))
						.userPoolId(System.getenv("booking_userpool"))
						.authParameters(new HashMap<String, String>() {{
							put("email", email);
							put("password", password);
						}})
						.build();
				try {
					AdminInitiateAuthResponse result = cognitoClient.adminInitiateAuth(signInRequest);
					String accessToken = result.authenticationResult().idToken();

					return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"accessToken\":\"" + accessToken + "\"}");
				} catch (Exception e) {
					return new APIGatewayProxyResponseEvent().withStatusCode(403).withBody("{\"message\":\"Access denied: " + e.getMessage() +  "\"}");
				}
			}
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(400)
					.withBody("Invalid resource or method.");
		}

	private String getClientId(CognitoIdentityProviderClient cognitoClient) {
		ListUserPoolClientsRequest listUserPoolClientsRequest = ListUserPoolClientsRequest.builder()
				.userPoolId(System.getenv("booking_userpool"))
				.maxResults(60)
				.build();

		ListUserPoolClientsResponse response = cognitoClient.listUserPoolClients(listUserPoolClientsRequest);

		if (!response.userPoolClients().isEmpty()) {
			return response.userPoolClients().get(0).clientId();
		}
		return null;
	}


	}
