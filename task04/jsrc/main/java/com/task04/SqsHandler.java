package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.syndicate.deployment.annotations.events.SqsTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;

@LambdaHandler(lambdaName = "sqs_handler",
	roleName = "sqs_handler-role"
)
@SqsTriggerEventSource(targetQueue = "async_queue", batchSize = 10)
public class SqsHandler implements RequestHandler<SQSEvent, Void> {

	@Override
	public Void handleRequest(SQSEvent sqsEvent, Context context) {
		for (SQSMessage msg : sqsEvent.getRecords()) {
			processMessage(msg, context);
		}
		context.getLogger().log("done");
		return null;
	}

	private void processMessage(SQSMessage msg, Context context) {
		try {
			context.getLogger().log(msg.getBody());
		} catch (Exception e) {
			context.getLogger().log("An error occurred");
			throw e;
		}
	}
}
