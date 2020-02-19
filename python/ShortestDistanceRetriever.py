import os
import logging
import boto3
from boto3.dynamodb.conditions import Key, Attr

logger = logging.getLogger()
logger.setLevel(logging.INFO)

# --- Helper to build the response ---

def close(fulfillment_state, message):
    response = {
        'dialogAction': {
            'type': 'Close',
            'fulfillmentState': fulfillment_state,
            'message': message
        }
    }

    return response

# --- Helper Functions ---

def getShortestDistanceBetweenCities(intent_request):
    source = (lambda: intent_request['currentIntent']['slots']['Source'])
    destination = (lambda: intent_request['currentIntent']['slots']['Destination'])

    dynamodb = boto3.resource('dynamodb', region_name='us-east-1')
    table = dynamodb.Table(os.environ['TABLE_NAME'])
    response = table.query(
        KeyConditionExpression=Key('SourceCity').eq(source) & Key('DestinationCity').eq(destination)
    )

    result = -1

    for i in response['Items']:
        result = i['ShortestDistance']

    return close(
        'Fulfilled',
        {
            'contentType': 'PlainText',
            'content': str(result)
        }
    )



# --- Intent ---


def dispatch(intent_request):
    intent_name = intent_request['currentIntent']['name']

    # Dispatch to bot's intent handler
    if intent_name == 'ShortestDistanceBetweenCities':
        return getShortestDistanceBetweenCities(intent_request)

    raise Exception('Intent with name ' + intent_name + ' not supported')


# --- Main handler ---


def lambda_handler(event, context):
    return dispatch(event)
