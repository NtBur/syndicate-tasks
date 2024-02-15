package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.syndicate.deployment.annotations.LambdaUrlConfig;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task08.response.ErrorMessage;
import com.task08.response.WeatherMessage;


import java.util.Map;


@LambdaHandler(lambdaName = "api_handler",
		roleName = "api_handler-role"
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
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
					.withBody(gson.toJson(new WeatherMessage().getWeather()));
		} catch (IllegalArgumentException exception) {
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(SC_BAD_REQUEST)
					.withBody(gson.toJson(new ErrorMessage(exception.getMessage())));
		}
	}
}
