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
import java.util.*;
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
        } else if ("/tables".equals(event.getResource()) && "GET".equals(event.getHttpMethod())) {
            return handleGetTables(event);
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
        } catch (InvalidParameterException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid parameter");
        } catch (InvalidPasswordException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid password");
        }
    }

    private APIGatewayProxyResponseEvent handleSignin(APIGatewayProxyRequestEvent inputBody) {
        try {
            changeTemporaryPassword(inputBody);
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

            String token = authResponse.authenticationResult().idToken();
            System.out.println("TOKEN " + token);
            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"accessToken\":\"" + token + "\"}");
        } catch (UsernameExistsException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Username already exists");
        } catch (InvalidParameterException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid parameter");
        } catch (InvalidPasswordException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid password");
        } catch (InvalidEmailRoleAccessPolicyException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid email");
        } catch (InvalidLambdaResponseException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid lambda response");
        } catch (InvalidOAuthFlowException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid AuthFlow");
        } catch (InvalidUserPoolConfigurationException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid UserPoolConfiguration");
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

    private APIGatewayProxyResponseEvent handleGetTables(APIGatewayProxyRequestEvent inputBody) {
        try {
           String token = inputBody.getHeaders().get("Authorization");
            GetUserRequest getUserRequest = GetUserRequest.builder().accessToken(token).build();
            cognitoClient.getUser(getUserRequest);

            List<Map<String, Object>> tables = getTables();

            Map<String, Object> response = Collections.singletonMap("tables", tables);
            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(response.toString());
        } catch (NotAuthorizedException ex) {
            return new APIGatewayProxyResponseEvent().withStatusCode(401).withBody("Not authorized");
        } catch (ResourceNotFoundException ex) {
            return new APIGatewayProxyResponseEvent().withStatusCode(404).withBody("User not found");
        } catch (CognitoIdentityProviderException ex) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody(ex.getMessage());
        } catch (Exception ex) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Internal server error");
        }
    }

    private List<Map<String, Object>> getTables() {
        List<Map<String, Object>> tables = new ArrayList<>();
        Map<String, Object> table1 = new HashMap<>();
        table1.put("id", 15728);
        table1.put("number", 1);
        table1.put("places", 8);
        table1.put("isVip", true);
        table1.put("minOrder", 1000);
        tables.add(table1);

        Map<String, Object> table2 = new HashMap<>();
        table2.put("id", 15729);
        table2.put("number", 2);
        table2.put("places", 6);
        table2.put("isVip", false);
        table2.put("minOrder", 500);
        tables.add(table2);

        Map<String, Object> table3 = new HashMap<>();
        table3.put("id", 15730);
        table3.put("number", 3);
        table3.put("places", 10);
        table3.put("isVip", false);
        table3.put("minOrder", 800);
        tables.add(table3);

        return tables;
    }
}