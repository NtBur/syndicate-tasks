package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.syndicate.deployment.annotations.LambdaUrlConfig;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.Map;

@LambdaHandler(lambdaName = "hello_world",
        roleName = "hello_world-role",
        isPublishVersion = true
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
public class HelloWorld implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final int SC_OK = 200;
    private static final int SC_BAD_REQUEST = 400;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, Context context) {
        context.getLogger().log(apiGatewayProxyRequestEvent.toString());
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(SC_OK)
                    .withBody("Hello from Lambda");
        } catch (IllegalArgumentException exception) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(SC_BAD_REQUEST)
                    .withBody(exception.getMessage());
        }
    }
}
