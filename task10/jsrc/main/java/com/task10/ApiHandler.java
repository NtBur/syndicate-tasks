package com.task10;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
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
    private AmazonDynamoDB amazonDynamoDB;


    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        this.initDynamoDbClient();

        if ("/signup".equals(event.getResource()) && "POST".equals(event.getHttpMethod())) {
            return handleSignup(event);
        } else if ("/signin".equals(event.getResource()) && "POST".equals(event.getHttpMethod())) {
            return handleSignin(event);
        } else if ("/tables".equals(event.getResource()) && "GET".equals(event.getHttpMethod())) {
            return handleGetTables();
        } else if ("/tables".equals(event.getResource()) && "POST".equals(event.getHttpMethod())) {
            return handlePostTables(event);
        } else if ("/tables/{tableId}".equals(event.getResource()) && "GET".equals(event.getHttpMethod())) {
            return handleGetTableById(event);
        } else if ("/reservations".equals(event.getResource()) && "POST".equals(event.getHttpMethod())) {
            return handlePostReservations(event);
        } else if ("/reservations".equals(event.getResource()) && "GET".equals(event.getHttpMethod())) {
            return handleGetReservations();
        }

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(400)
                .withBody("Invalid resource or method.");
    }

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
        } catch (EnableSoftwareTokenMfaException | SoftwareTokenMfaNotFoundException |
                 UnsupportedTokenTypeException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid Token");
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

    private APIGatewayProxyResponseEvent handleGetTables() {

        try {
            ScanRequest scanRequest = new ScanRequest().withTableName(System.getenv("tables_table"));
            ScanResult scanResult = amazonDynamoDB.scan(scanRequest);
            List<Map<String, Object>> tables = new ArrayList<>();
            for (Map<String, AttributeValue> item : scanResult.getItems()) {
                Map<String, Object> table = new HashMap<>();
                table.put("id", Integer.parseInt(item.get("id").getN()));
                table.put("number", Integer.parseInt(item.get("number").getN()));
                table.put("places", Integer.parseInt(item.get("places").getN()));
                table.put("isVip", Boolean.parseBoolean(item.get("isVip").getBOOL().toString()));

                if (item.containsKey("minOrder")) {
                    table.put("minOrder", Integer.parseInt(item.get("minOrder").getN()));
                }
                tables.add(table);
            }

            Map<String, List<Map<String, Object>>> responseBody = new HashMap<>();
            responseBody.put("tables", tables);

            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(responseBody.toString());
        } catch (NotAuthorizedException ex) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Not authorized");
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody(e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent handlePostTables(APIGatewayProxyRequestEvent inputBody) throws ConditionalCheckFailedException {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Map<String, String> input = gson.fromJson(inputBody.getBody(), new TypeToken<Map<String, String>>() {
            }.getType());
            Map<String, AttributeValue> attributesMap = new HashMap<>();

            String id = input.get("id");
            attributesMap.put("id", new AttributeValue().withN(id));
            attributesMap.put("number", new AttributeValue().withN(String.valueOf(input.get("number"))));
            attributesMap.put("places", new AttributeValue().withN(String.valueOf(input.get("places"))));
            attributesMap.put("isVip", new AttributeValue().withBOOL(Boolean.valueOf((input.get("isVip")))));
            if (input.containsKey("minOrder")) {
                String minOrder = String.valueOf(input.get("minOrder"));
                attributesMap.put("minOrder", new AttributeValue().withN(minOrder));
            }
            amazonDynamoDB.putItem(System.getenv("tables_table"), attributesMap);
            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"id\":\"" + id + "\"}");
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("{\"Invalid input\":\"" + e.getMessage() + "\"}");
        }
    }

    private void initDynamoDbClient() {
        this.amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withRegion(System.getenv("region"))
                .build();
    }

    public APIGatewayProxyResponseEvent handleGetTableById(APIGatewayProxyRequestEvent event) {
        try {
            String tableId = event.getPathParameters().get("tableId");

            Map<String, AttributeValue> item = getItemFromDynamoDB(tableId);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("id", Integer.parseInt(item.get("id").getN()));
            responseBody.put("number", Integer.parseInt(item.get("number").getN()));
            responseBody.put("places", Integer.parseInt(item.get("places").getN()));
            responseBody.put("isVip", Boolean.parseBoolean(item.get("isVip").getBOOL().toString()));

            if (item.containsKey("minOrder")) {
                responseBody.put("minOrder", Integer.parseInt(item.get("minOrder").getN()));
            }

            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(responseBody.toString());

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody(e.getMessage());
        }
    }

    private Map<String, AttributeValue> getItemFromDynamoDB(String tableId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", new AttributeValue().withN(tableId));

        return amazonDynamoDB.getItem(System.getenv("tables_table"), key).getItem();
    }

    public APIGatewayProxyResponseEvent handlePostReservations(APIGatewayProxyRequestEvent inputBody) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Map<String, String> input = gson.fromJson(inputBody.getBody(), new TypeToken<Map<String, String>>() {
            }.getType());
            int tableNumber = Integer.parseInt(input.get("tableNumber"));
            if (tableNumberExists(tableNumber)) {
                String reservationId = String.valueOf(UUID.randomUUID());

                Map<String, AttributeValue> reservationItem = new HashMap<>();
                reservationItem.put("id", new AttributeValue(reservationId));
                reservationItem.put("tableNumber", new AttributeValue().withN(String.valueOf(tableNumber)));
                reservationItem.put("clientName", new AttributeValue().withS(String.valueOf(input.get("clientName"))));
                reservationItem.put("phoneNumber", new AttributeValue().withS(String.valueOf(input.get("phoneNumber"))));
                reservationItem.put("date", new AttributeValue().withS(String.valueOf(input.get("date"))));
                reservationItem.put("slotTimeStart", new AttributeValue().withS(String.valueOf(input.get("slotTimeStart"))));
                reservationItem.put("slotTimeEnd", new AttributeValue().withS(String.valueOf(input.get("slotTimeEnd"))));

                amazonDynamoDB.putItem(System.getenv("reservations_table"), reservationItem);
                return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"reservationId\": \"" + reservationId + "\"}");
            } else {
                throw new TableNumberNotFoundException("Table number '" + tableNumber + "' not found.");
            }

        } catch (TableNumberNotFoundException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("reservation can't be created");
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody(e.getMessage());
        }
    }

    private boolean tableNumberExists(int tableNumber) {
        String tableNumberString = String.valueOf(tableNumber);
        ScanRequest scanRequest = new ScanRequest().withTableName(System.getenv("tables_table"));
        ScanResult scanResult = amazonDynamoDB.scan(scanRequest);
        for (Map<String, AttributeValue> item : scanResult.getItems()) {
            if (item.containsKey("number")) {
                if (item.get("number").getN().equals(tableNumberString)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class TableNumberNotFoundException extends Exception {
        public TableNumberNotFoundException(String message) {
            super(message);
        }
    }

    public APIGatewayProxyResponseEvent handleGetReservations() {
        try {
            ScanRequest scanRequest = new ScanRequest().withTableName(System.getenv("reservations_table"));
            ScanResult scanResult = amazonDynamoDB.scan(scanRequest);
            List<Map<String, Object>> reservations = new ArrayList<>();
            for (Map<String, AttributeValue> item : scanResult.getItems()) {
                Map<String, Object> reservation = new HashMap<>();
                reservation.put("tableNumber", Integer.parseInt(item.get("tableNumber").getN()));
                reservation.put("clientName", item.get("clientName").getS());
                reservation.put("phoneNumber", item.get("phoneNumber").getS());
                reservation.put("date", item.get("date").getS());
                reservation.put("slotTimeStart", item.get("slotTimeStart").getS());
                reservation.put("slotTimeEnd", item.get("slotTimeEnd").getS());

                reservations.add(reservation);
            }
            Map<String, List<Map<String, Object>>> responseBody = new HashMap<>();
            responseBody.put("reservations", reservations);

            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(responseBody.toString());
        } catch (NotAuthorizedException ex) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Not authorized");
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody(e.getMessage());
        }
    }
}
