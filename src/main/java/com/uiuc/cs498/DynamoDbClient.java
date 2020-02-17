package com.uiuc.cs498;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.TableDescription;

import java.util.Iterator;
import java.util.List;

public class DynamoDbClient {

    private static DynamoDB dynamoDB;

    private static final DynamoDbClient client = new DynamoDbClient();

    private DynamoDbClient() {
        AmazonDynamoDBClient dynamoDBClient = (AmazonDynamoDBClient) AmazonDynamoDBClientBuilder.defaultClient();
        dynamoDB = new DynamoDB(dynamoDBClient);

    }

    public static DynamoDbClient getInstance() {
        return client;
    }

    public void flushTable(String tableName) {
        Table table = dynamoDB.getTable(tableName);
        String strPartitionKey = null;
        String strSortKey = null;
        TableDescription description = table.describe();
        List<KeySchemaElement> schema = description.getKeySchema();
        for (KeySchemaElement element : schema) {
            if (element.getKeyType().equalsIgnoreCase(Constants.HASH))
                strPartitionKey = element.getAttributeName();
            if (element.getKeyType().equalsIgnoreCase(Constants.RANGE))
                strSortKey = element.getAttributeName();
        }

        ItemCollection<ScanOutcome> deleteoutcome = table.scan();
        Iterator<Item> iterator = deleteoutcome.iterator();
        while (iterator.hasNext()) {
            Item next = iterator.next();
            if (strSortKey == null && strPartitionKey != null)
                table.deleteItem(strPartitionKey, next.get(strPartitionKey));
            else if (strPartitionKey != null && strSortKey != null)
                table.deleteItem(strPartitionKey, next.get(strPartitionKey), strSortKey, next.get(strSortKey));
        }

    }

    public void addEntryToDynamoDb(String tableName, Item item) {
        dynamoDB.getTable(tableName).putItem(new PutItemSpec().withItem(item));
    }
}
