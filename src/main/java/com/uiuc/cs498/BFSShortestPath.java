package com.uiuc.cs498;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.apache.http.HttpStatus;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

import static com.uiuc.cs498.Constants.*;

public class BFSShortestPath implements RequestStreamHandler {

    private DynamoDbClient dynamoDbClient = DynamoDbClient.getInstance();
    private LambdaLogger logger;

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        JSONObject response = new JSONObject();

        logger = context.getLogger();

        try {
            String payload = (String) extractEventPayload(inputStream, BODY);
            logger.log(payload);
            processGraph(payload);

            response.put(STATUS_CODE, HttpStatus.SC_OK);
            response.put(BODY, "Done!");

        } catch (InterruptedException | IOException e) {

            logger.log(getExceptionStackTrace(e));
            response.put(STATUS_CODE, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.put(BODY, "An error occurred!");

        }

        OutputStreamWriter streamWriter = new OutputStreamWriter(outputStream);
        streamWriter.write(response.toString());
        streamWriter.close();

    }

    private Object extractEventPayload(InputStream inputStream, String payloadField) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            stringBuilder.append(line);
        }
        br.close();
        return new JSONObject(stringBuilder.toString()).get(payloadField);
    }


    private void processGraph(String payload) throws InterruptedException {
        Graph graph = buildGraph(payload);
        if (graph != null) {
            clearTableContents(TABLE_DISTANCE);
            processAllCitiesShortestDistanceDetails(graph);

        }
    }

    private Graph buildGraph(String payload) {
        String graph = new JSONObject(payload).getString(GRAPH);
        if (graph != null) {
            List<Vertex> vertices = new ArrayList<Vertex>();
            for (String edge : graph.split(REGEX_EDGE_DELIMETER)) {
                String[] pair = edge.split(REGEX_VERTEX_PAIR_DELIMETER);
                Vertex vertexTo = new Vertex(pair[1]);
                Vertex vertexFrom = new Vertex(pair[0]);
                int vertexFromIndex;
                if ((vertexFromIndex = vertices.indexOf(vertexFrom)) != -1) {
                    vertices.get(vertexFromIndex).getNeighbours().add(vertexTo);
                } else {
                    vertexFrom.addNeighbour(vertexTo);
                    vertices.add(vertexFrom);
                }
                if (!vertices.contains(vertexTo)) {
                    vertices.add(vertexTo);
                }

            }
            return new Graph(vertices);
        }
        return null;
    }


    private void clearTableContents(String table) {
        dynamoDbClient.flushTable(table);
    }


    private void processAllCitiesShortestDistanceDetails(Graph graph) {
        for (Vertex vertex : graph.getVertices()) {
            logger.log("Processing vertex " + vertex + ":");
            Map<Vertex, Integer> shortestDistance = findShortestDistanceFromGivenVertex(graph, vertex);
            logger.log(shortestDistance.toString());
            persistRecordsToTable(TABLE_DISTANCE, vertex, shortestDistance);
        }
    }

    private Map<Vertex, Integer> findShortestDistanceFromGivenVertex(Graph graph, Vertex source) {
        Map<Vertex, Integer> minimumDistance = new HashMap<>();
        minimumDistance.put(source, ZERO);

        Queue<Vertex> queue = new LinkedList<Vertex>();
        queue.add(source);

        Set<Vertex> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Vertex vertex = queue.poll();
            visited.add(vertex);
            int index = graph.getVertices().indexOf(vertex);
            for (Vertex neighbour : graph.getVertices().get(index).getNeighbours()) {
                minimumDistance.put(neighbour,
                        Math.min(minimumDistance.get(vertex) + 1, minimumDistance.getOrDefault(neighbour, Integer.MAX_VALUE)));
                if (!visited.contains(neighbour)) queue.add(neighbour);
            }
        }
        return minimumDistance;
    }

    private void persistRecordsToTable(String tableName, Vertex source, Map<Vertex, Integer> shortestDistance) {
        for (Map.Entry entry : shortestDistance.entrySet()) {
            dynamoDbClient.addEntryToDynamoDb(tableName, new Item()
                    .withString(SOURCE, source.getValue())
                    .withString(DESTINATION, ((Vertex) entry.getKey()).getValue())
                    .withNumber(DISTANCE, (Integer) entry.getValue()));
        }
    }

    private String getExceptionStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }



    public static void main(String s[]) throws InterruptedException {
        String payload = "{\"graph\": " +
                "\"a1->a2,a2->a3,a3->a4,a1->a5,a1->a6,a2->a7,a3->a7,a6->a8," +
                "a9->a2,a9->a4,a10->a11,a11->a5,a7->a11,a12->a2,a9->a12\"}";
        BFSShortestPath shortestPath = new BFSShortestPath();
        shortestPath.processGraph(payload);
    }

}

class Vertex {
    private String value;
    private List<Vertex> neighbours;

    Vertex(String value) {
        this.value = value;
        this.neighbours = new ArrayList<Vertex>(0);
    }

    public Vertex(String value, List<Vertex> neighbours) {
        this.value = value;
        this.neighbours = neighbours;
    }

    public void addNeighbour(Vertex neighbour) {
        this.neighbours.add(neighbour);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vertex)) return false;
        Vertex vertex = (Vertex) o;
        return value.equals(vertex.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }

    public String getValue() {
        return value;
    }

    public List<Vertex> getNeighbours() {
        return neighbours;
    }
}


class Graph {
    private List<Vertex> vertices;

    public Graph(List<Vertex> vertices) {
        this.vertices = vertices;
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

}
