package com.task10;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolClientsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolClientsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolClientDescription;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolDescriptionType;

import java.util.List;

public class CognitoClient {
    private UserPoolDescriptionType getUserName(List<UserPoolDescriptionType> userPools, String nameUserPool) {
        return userPools.stream().filter(userPool -> (userPool.name()).equals(nameUserPool)).findFirst().get();
    }

    public String getUserPoolId(CognitoIdentityProviderClient client, String userPoolName) {
        ListUserPoolsRequest request = ListUserPoolsRequest.builder()
                .maxResults(8)
                .build();

        ListUserPoolsResponse response = client.listUserPools(request);
        List<UserPoolDescriptionType> userPools = response.userPools();

        if (userPools.isEmpty()) {
            throw new RuntimeException("User pools is empty");
        }
        UserPoolDescriptionType userPool = getUserName(userPools, userPoolName);
        return userPool.id();
    }


    public String getClientId(CognitoIdentityProviderClient client, String userPoolId) {
        ListUserPoolClientsRequest request = ListUserPoolClientsRequest.builder()
                .userPoolId(userPoolId)
                .maxResults(1)
                .build();

        ListUserPoolClientsResponse response = client.listUserPoolClients(request);
        List<UserPoolClientDescription> userPoolClients = response.userPoolClients();

        if (userPoolClients.isEmpty()) {
            throw new RuntimeException("User pool clients is empty");
        }

        UserPoolClientDescription userPoolClient = userPoolClients.get(0);
        return userPoolClient.clientId();
    }

}
