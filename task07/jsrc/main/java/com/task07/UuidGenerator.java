package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

@LambdaHandler(lambdaName = "uuid_generator",
	roleName = "uuid_generator-role"
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "target_bucket", value = "${target_bucket}")
})
@RuleEventSource(targetRule = "uuid_trigger")
public class UuidGenerator implements RequestHandler<Void, Void> {
	private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
	private static final String IDS = "ids";

	public Void handleRequest(Void event, Context context) {
		String bucketName = System.getenv("target_bucket");
		JSONArray array = new JSONArray();
		for(int i = 0; i < 10; i++) {
			array.put(UUID.randomUUID());
		}
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(IDS, array);
		String jsonString = jsonObject.toString();

		Instant startTime = Instant.now();
		String fileName = startTime.toString() + ".txt";
		InputStream inputStream = new ByteArrayInputStream(jsonString.toString().getBytes(StandardCharsets.UTF_8));
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(jsonString.length());
		s3.putObject(bucketName, fileName, inputStream, metadata);

		return null;
	}
}
