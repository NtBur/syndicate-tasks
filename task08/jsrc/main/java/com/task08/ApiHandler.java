package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.syndicate.deployment.annotations.LambdaUrlConfig;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task08.response.ErrorMessage;
import com.task08.response.WeatherMessage;




@LambdaHandler(lambdaName = "api_handler",
		roleName = "api_handler-role",
		layers = {"sdk-layer"}
)
@LambdaLayer(
		layerName = "sdk-layer",
		libraries = {"lib/commons-lang3-3.14.0.jar", "lib/gson-2.10.1.jar"},
		runtime = DeploymentRuntime.JAVA8,
		artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	private static final int SC_OK = 200;
	private static final int SC_BAD_REQUEST = 400;
	private final Gson gson = new Gson();

	private final String BASE_URL = "https://api.open-meteo.com/v1/forecast?latitude=50.4547&longitude=30.5238&hourly=temperature_2m&timezone=Europe%2FKyiv";
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, Context context) {
		context.getLogger().log(apiGatewayProxyRequestEvent.toString());
		String url = gson.toJson(BASE_URL);
		try {
			return (url!=null)?new APIGatewayProxyResponseEvent()
					.withStatusCode(SC_OK)
					.withBody(new WeatherMessage().getWeather()):null;
		} catch (IllegalArgumentException exception) {
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(SC_BAD_REQUEST)
					.withBody(gson.toJson(new ErrorMessage(exception.getMessage())));
		}
	}
}
