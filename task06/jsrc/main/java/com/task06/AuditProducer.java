package com.task06;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
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
public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {
    private AmazonDynamoDB amazonDynamoDB;

    public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
        this.initDynamoDbClient();
        for (DynamodbStreamRecord record : dynamodbEvent.getRecords()) {

            if (record.getEventName().equals("INSERT")) {
                updateAuditForInsert(record);
            } else if (record.getEventName().equals("MODIFY")) {
                updateAuditForModify(record);
            }
        }

        return null;
    }

    private Map<String, AttributeValue> updateAuditForInsert(DynamodbStreamRecord record) throws ConditionalCheckFailedException {
        Map<String, AttributeValue> attributesMap = new HashMap<>();
        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newTable = record.getDynamodb().getNewImage();

        attributesMap.put("id", new AttributeValue(String.valueOf(UUID.randomUUID())));
        attributesMap.put("itemKey", new AttributeValue(String.valueOf(newTable.get("key").getS())));
        attributesMap.put("modificationTime", new AttributeValue(String.valueOf(Instant.now())));
        Map<String, AttributeValue> newValues = new HashMap<>();
        newValues.put("key", new AttributeValue(String.valueOf(newTable.get("key").getS())));
        newValues.put("value", new AttributeValue(String.valueOf(newTable.get("value").getS())));
        attributesMap.put("newValue", new AttributeValue().withM(newValues));

        amazonDynamoDB.putItem(System.getenv("target_table"), attributesMap);
        return attributesMap;
    }

    private Map<String, AttributeValue> updateAuditForModify(DynamodbStreamRecord record) throws ConditionalCheckFailedException {
        Map<String, AttributeValue> attributesMap = new HashMap<>();
        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> oldTable = record.getDynamodb().getOldImage();
        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newTable = record.getDynamodb().getNewImage();

        attributesMap.put("id", new AttributeValue(String.valueOf(UUID.randomUUID())));
        attributesMap.put("itemKey", new AttributeValue(String.valueOf(newTable.get("key").getS())));
        attributesMap.put("modificationTime", new AttributeValue(String.valueOf(Instant.now())));

        attributesMap.put("updatedAttribute", new AttributeValue("value"));
        attributesMap.put("oldValue", new AttributeValue(String.valueOf(oldTable.get("value").getS())));
        attributesMap.put("newValue", new AttributeValue(String.valueOf(newTable.get("value").getS())));

        amazonDynamoDB.putItem(System.getenv("target_table"), attributesMap);
        return attributesMap;
    }

    private void initDynamoDbClient() {
        this.amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withRegion(System.getenv("region"))
                .build();
    }
}

