package com.task06;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(lambdaName = "audit_producer",
        roleName = "audit_producer-role"
)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "region", value = "${region}"),
        @EnvironmentVariable(key = "target_table", value = "${target_table}")
})
@DynamoDbTriggerEventSource(targetTable = "Configuration", batchSize = 1)
public class AuditProducer implements RequestHandler<DynamodbEvent, Map<String, AttributeValue>> {
    private AmazonDynamoDB amazonDynamoDB;

    public Map<String, AttributeValue> handleRequest(DynamodbEvent dynamodbEvent, Context context) {
        this.initDynamoDbClient();
        for (DynamodbEvent.DynamodbStreamRecord record : dynamodbEvent.getRecords()) {
            if (record != null) {
               return createAudit(record);
            }
        }
        return null;
    }

    private Map<String, AttributeValue> createAudit(DynamodbEvent.DynamodbStreamRecord record) throws ConditionalCheckFailedException {

        Map<String, AttributeValue> attributesMap = new HashMap<>();
        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> oldTable = record.getDynamodb().getOldImage();
        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newTable = record.getDynamodb().getOldImage();

        attributesMap.put("id", new AttributeValue(String.valueOf(UUID.randomUUID())));
        attributesMap.put("itemKey", new AttributeValue(String.valueOf(oldTable.get("key"))));
        attributesMap.put("modificationTime", new AttributeValue(String.valueOf(Instant.now())));
        if (!oldTable.get("value").equals(newTable.get("value"))) {
            attributesMap.put("updatedAttribute", new AttributeValue("value"));
            attributesMap.put("oldValue", new AttributeValue(String.valueOf(oldTable.get("value"))));
            attributesMap.put("newValue", new AttributeValue(String.valueOf(newTable.get("value"))));
        } else {
            Map<String, AttributeValue> newValues = new HashMap<>();
            newValues.put("key", new AttributeValue(String.valueOf(newTable.get("key"))));
            newValues.put("value", new AttributeValue(String.valueOf(newTable.get("value"))));
            attributesMap.put("newValue", new AttributeValue().withM(newValues));
        }
        amazonDynamoDB.putItem(System.getenv("target_table"), attributesMap);
        return attributesMap;

    }

    private void initDynamoDbClient() {
        this.amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withRegion(System.getenv("region"))
                .build();
    }
}

