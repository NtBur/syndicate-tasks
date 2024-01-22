package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.syndicate.deployment.annotations.events.SnsEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;


@LambdaHandler(lambdaName = "sns_handler",
	roleName = "sns_handler-role"
)
@SnsEventSource(targetTopic = "lambda_topic")
public class SnsHandler implements RequestHandler<SNSEvent, Void> {

	@Override
	public Void handleRequest(SNSEvent snsEvent, Context context) {
		for (SNSEvent.SNSRecord sns : snsEvent.getRecords()) {
			processMessage(sns, context);
		}
		context.getLogger().log("done");
		return null;
	}

	private void processMessage(SNSEvent.SNSRecord sns, Context context) {
		try {
			context.getLogger().log(sns.getSNS().getMessage());
		} catch (Exception e) {
			context.getLogger().log("An error occurred");
			throw e;
		}
	}
}
