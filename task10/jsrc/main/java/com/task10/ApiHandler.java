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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@LambdaHandler(lambdaName = "api_handler",
        roleName = "api_handler-role"
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
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


    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if ("/signup".equals(event.getResource()) && "POST".equals(event.getHttpMethod())) {
            return handleSignup(event);
        } else if ("/signin".equals(event.getResource()) && "POST".equals(event.getHttpMethod())) {
            return handleSignin(event);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(400)
                .withBody("Invalid resource or method.");
    }

		/*	String path = input.getPath();

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
			return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid request");*/
    //	}

    private APIGatewayProxyResponseEvent handleSignup(APIGatewayProxyRequestEvent inputBody) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, String> input = gson.fromJson(inputBody.getBody(), new TypeToken<Map<String, String>>() {
        }.getType());
        try {
            String validPassword = validatePassword(input.get("password"));
            String validEmail = validateEmail(input.get("email"));

            AdminCreateUserRequest adminCreateUserRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .messageAction("SUPPRESS")
                    .username(validEmail)
                    .temporaryPassword(validPassword)
                    .userAttributes(
                            AttributeType.builder().name("name").value(input.get("firstName")).build(),
                            AttributeType.builder().name("family_name").value(input.get("lastName")).build(),
                            AttributeType.builder().name("email").value(validEmail).build()
                    )
                    .build();

            AdminCreateUserResponse adminCreateUserResponse = cognitoClient.adminCreateUser(adminCreateUserRequest);
            return new APIGatewayProxyResponseEvent().withStatusCode(200);
        } catch (RuntimeException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400);
        }
    }

    public String validateEmail(String email) {
        String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";
        Pattern pattern = Pattern.compile(EMAIL_PATTERN);
        Matcher matcher = pattern.matcher(email);
        if (matcher.matches()) {
            return email;
        } else {
            throw new RuntimeException("invalid email");
        }
    }

    public String validatePassword(String password) {
        String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[$%^*])[A-Za-z\\d$%^*]{12,}$";
        Pattern pattern = Pattern.compile(PASSWORD_PATTERN);
        Matcher matcher = pattern.matcher(password);
        if (matcher.matches()) {
            return password;
        } else {
            throw new RuntimeException("invalid password");
        }
    }

    private APIGatewayProxyResponseEvent handleSignin(APIGatewayProxyRequestEvent inputBody) {
        changeTemporaryPassword(inputBody);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, String> input = gson.fromJson(inputBody.getBody(), new TypeToken<Map<String, String>>() {
        }.getType());
        try {
            AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(
                    AdminInitiateAuthRequest.builder()
                            .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                            .clientId(clientId)
                            .userPoolId(userPoolId)
                            .authParameters(buildAuthParameters(input.get("email"), input.get("password")))
                            .build()
            );

            String token = authResponse.authenticationResult().idToken();
            System.out.println("TOKEN " + token);
            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"accessToken\":\"" + token + "\"}");
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Auth failed: " + e.getMessage());
        }
    }


    private Map<String, String> buildAuthParameters(String username, String password) {
        Map<String, String> authParameters = new HashMap<>();
        authParameters.put("USERNAME", username);
        authParameters.put("PASSWORD", password);
        return authParameters;
    }

    private void changeTemporaryPassword(APIGatewayProxyRequestEvent inputBody) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, String> input = gson.fromJson(inputBody.getBody(), new TypeToken<Map<String, String>>() {
        }.getType());
        AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(
                AdminInitiateAuthRequest.builder()
                        .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                        .clientId(clientId)
                        .userPoolId(userPoolId)
                        .authParameters(buildAuthParameters(input.get("email"), input.get("password")))
                        .build()
        );

        String challengeName = authResponse.challengeNameAsString();

        if (ChallengeNameType.NEW_PASSWORD_REQUIRED.name().equals(challengeName)) {
            RespondToAuthChallengeResponse respondToAuthChallenge = cognitoClient.respondToAuthChallenge(
                    RespondToAuthChallengeRequest.builder()
                            .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                            .clientId(clientId)
                            .challengeResponses(new HashMap<String, String>() {
                                {
                                    put("USERNAME", input.get("email"));
                                    put("NEW_PASSWORD", input.get("password"));
                                }
                            })
                            .session(authResponse.session())
                            .build()
            );
        }
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