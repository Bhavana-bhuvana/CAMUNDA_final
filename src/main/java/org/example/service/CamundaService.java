package org.example.service;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.example.MongoUtil;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CamundaService {
    public static void startCamundaAndRunDMN() {
        try {
            // Step 1: Start Camunda BPM Run (if not already running)
            File camundaPath = new File("C:\\camunda\\camunda-bpm-run-7.13.0"); // Correct path
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "start.bat"); // Use "start.bat"
            processBuilder.directory(camundaPath);
            processBuilder.start();
            Thread.sleep(5000); // Wait for Camunda to start

            // Step 2: Call Camunda REST API to execute DMN
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void callCamunda(JsonObject extractedData, String dmnKey, JsonArray allResponses) {
        try {
            System.out.println(extractedData+"table key"+dmnKey+allResponses);
            String apiUrl = "http://localhost:8080/engine-rest/decision-definition/key/" + dmnKey + "/evaluate";
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Send JSON Data to Camunda
            String jsonInput = extractedData.toString();
            System.out.println("json going "+jsonInput);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            JsonObject variablesObject = extractedData.getAsJsonObject("variables");
            String testName = variablesObject.keySet().iterator().next();
            System.out.println("DMN executed for " + testName + " with response code: " + responseCode);

            // Read and store the response
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                System.out.println("Camunda Response: " + response);

                // Convert response to JSON and add to the array
                allResponses.add(response.toString());

            } else {
                System.out.println("Error: Camunda returned response code " + responseCode);
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static JsonObject finalOutput(JsonArray allResponses, ObjectId id) {
        JsonArray insights = new JsonArray();
        double totalScore = 0;
        int testCount = 0;

        MongoCollection<Document> collection = MongoUtil.getDatabase().getCollection("user");
        Document document = collection.find(Filters.eq("_id", id)).first();

        if (document == null) {
            throw new RuntimeException("Document not found for ID: " + id.toHexString());
        }

        // Prepare test list with "electrolytes" added twice if present
        List<String> testList = document.keySet().stream()
                .filter(key -> !key.equals("_id") && !key.equals("finalResult"))
                .flatMap(key -> {
                    if (key.equals("electrolytes")) {
                        return Stream.of("electrolytes", "electrolytes"); // Add twice
                    } else {
                        return Stream.of(key);
                    }
                })
                .collect(Collectors.toList());

        Set<String> validTestKeys = new HashSet<>(testList); // For quick lookup

        // Flatten and iterate through all responses
        List<JsonObject> allCamundaResults = new ArrayList<>();
        for (JsonElement resp : allResponses) {
            String responseStr = resp.getAsString();
            JsonArray responseArray = JsonParser.parseString(responseStr).getAsJsonArray();

            for (JsonElement element : responseArray) {
                allCamundaResults.add(element.getAsJsonObject());
            }
        }

        // Match responses with testList by index
        for (int i = 0; i < Math.min(testList.size(), allCamundaResults.size()); i++) {
            String testKey = testList.get(i);
            JsonObject responseObj = allCamundaResults.get(i);

            String riskCategory = responseObj.has("risk category")
                    ? responseObj.getAsJsonObject("risk category").get("value").getAsString()
                    : "No Risk Data";

            JsonObject scoreObj = null;
            if (responseObj.has("score")) {
                scoreObj = responseObj.getAsJsonObject("score");
            } else if (responseObj.has("Scores")) {
                scoreObj = responseObj.getAsJsonObject("Scores");
            }

            if (scoreObj != null) {
                double score = scoreObj.get("value").getAsDouble();
                String testName = testKey;

                // Special case for electrolytes → distinguish using risk category
                if ("electrolytes".equals(testKey)) {
                    if (riskCategory.toLowerCase().contains("sodium")) {
                        testName = "sodium";
                    } else if (riskCategory.toLowerCase().contains("potassium")) {
                        testName = "potassium";
                    } else {
                        testName = "electrolytes"; // fallback if unknown
                    }
                }

                JsonObject insight = new JsonObject();
                insight.addProperty("Test Name", testName);
                insight.addProperty("Risk Category", riskCategory);
                insight.addProperty("Score", score);
                insights.add(insight);

                totalScore += score;
                testCount++;
            }
        }

        double averageScore = testCount > 0 ? totalScore / testCount : 0;

        JsonObject finalResult = new JsonObject();
        finalResult.addProperty("Average Score", averageScore);
        finalResult.add("Insights", insights);

        // Save to MongoDB
        Document finalResultDoc = Document.parse(finalResult.toString());
        collection.updateOne(Filters.eq("_id", id), Updates.set("finalResult", finalResultDoc));

        return finalResult;
    }




}
