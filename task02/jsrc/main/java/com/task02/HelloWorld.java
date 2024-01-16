package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(lambdaName = "hello_world",
	roleName = "hello_world-role",
	isPublishVersion = true
)
public class HelloWorld implements RequestHandler<Object, Map<String, Object>> {
	private static final int SC_OK = 200;
    private static final int SC_BAD_REQUEST = 400;
    private final Gson gson = new Gson();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, Context context) {
        context.getLogger().log(apiGatewayProxyRequestEvent.toString());
        Map<String, String> queryStringParameters = apiGatewayProxyRequestEvent.getQueryStringParameters();
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(SC_OK)
                    .withBody(gson.toJson("Hello from Lambda"));
        } catch (IllegalArgumentException exception) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(SC_BAD_REQUEST)
                    .withBody(gson.toJson(exception.getMessage()));
        }
    }
}
