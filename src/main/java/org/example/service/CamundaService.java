package org.example.service;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.*;;
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
    public static JsonObject finalOutput(JsonArray allResponses, JsonArray extractedTests) {
        JsonArray insights = new JsonArray();
        double totalScore = 0;
        int testCount = 0;

        // Step 1: Extract test names from extractedTests
        List<String> testNames = new ArrayList<>();
        for (JsonElement testElement : extractedTests) {
            JsonObject testObj = testElement.getAsJsonObject().getAsJsonObject("variables");
            for (Map.Entry<String, JsonElement> entry : testObj.entrySet()) {
                testNames.add(entry.getKey());  // Store only the test name
            }
        }

        int testIndex = 0;  // To match extracted test names with responses

        // Step 2: Iterate through allResponses and match risk categories & scores
        for (int i = 0; i < allResponses.size(); i++) {
            String responseStr = allResponses.get(i).getAsString();
            JsonArray responseArray = JsonParser.parseString(responseStr).getAsJsonArray();

            for (int j = 0; j < responseArray.size(); j++) {
                JsonObject responseObj = responseArray.get(j).getAsJsonObject();

                // Get the corresponding test name
                String testName = (testIndex < testNames.size()) ? testNames.get(testIndex) : "Unknown Test";
                testIndex++;

                // Extract risk category
                String riskCategory = "No Risk Data";
                if (responseObj.has("risk category")) {
                    JsonObject riskObj = responseObj.getAsJsonObject("risk category");
                    riskCategory = riskObj.get("value").getAsString();
                }

                // Extract test score (from "Scores")
                int testScore = 0;
                if (responseObj.has("Scores")) {
                    JsonObject scoreObj = responseObj.getAsJsonObject("Scores");
                    testScore = scoreObj.get("value").getAsInt();  // Extract integer value
                    totalScore += testScore;
                    testCount++;
                }

                // Store insight
                JsonObject insight = new JsonObject();
                insight.addProperty("test_name", testName);
                insight.addProperty("risk_category", riskCategory);
                insight.addProperty("score", testScore); // Include score for each test
                insights.add(insight);

                // Special handling: If testName is "sodium", check for "potassium"
                if (testName.equalsIgnoreCase("sodium")) {
                    boolean potassiumFound = false;

                    // Check if "potassium" is also in the extracted test names
                    if (testIndex < testNames.size() && testNames.get(testIndex).equalsIgnoreCase("potassium")) {
                        potassiumFound = true;
                        testIndex++;  // Move index forward since we are handling potassium here
                    }

                    // If potassium is not found, add it manually with the same risk category
                    if (!potassiumFound) {
                        JsonObject potassiumInsight = new JsonObject();
                        potassiumInsight.addProperty("test_name", "potassium");
                        potassiumInsight.addProperty("risk_category", riskCategory);
                        potassiumInsight.addProperty("score", testScore); // Use same score as sodium
                        insights.add(potassiumInsight);
                    }
                }
            }
        }

        // Step 3: Compute final score
        double finalScore = (testCount > 0) ? totalScore / testCount : 0;

        // Step 4: Construct final JSON output
        JsonObject finalResult = new JsonObject();
        finalResult.addProperty("final_score", finalScore);
        finalResult.add("insights", insights);

        System.out.println(finalResult.toString());
        return finalResult;
    }



}
