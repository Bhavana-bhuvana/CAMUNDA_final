package org.example;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.example.service.CamundaService;
import org.example.service.TextExtractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.mongodb.client.*;


import static org.example.service.CamundaService.*;

@RestController
@RequestMapping("/")
public class HelloWorldController {
    private static final Map<String, String> DMN_KEYS = new HashMap<>();

    static {
        DMN_KEYS.put("bun", "Decision_01t35it");
        DMN_KEYS.put("creatinine", "Decision_0bpci3c");
        DMN_KEYS.put("electrolytes", "Decision_0oywdb5");
        DMN_KEYS.put("gfr", "Decision_1aazp67");
        DMN_KEYS.put("uacr", "Decision_1h0cvj7");
    }
    @Autowired
    public TextExtractionService textExtractionService;


    @Autowired
    public CamundaService camundaService;

    @PostMapping("/upload")
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file) {
        JsonArray allResponses = null;
        try {

            // Step 1: Extract text from file (using your existing logic)
            // Step 2: Convert extracted data into Camunda JSON format
            JsonArray camundaInput = textExtractionService.extractText(file).getAsJsonArray();
            // Step 3: Call Camunda API and return decision result

            JsonObject DnmObj = new JsonObject();
            allResponses = new JsonArray();

            MongoCollection<Document> collection = MongoUtil.getDatabase().getCollection("user");
            for (String testKey : DMN_KEYS.keySet()) {

                    Document doc = collection.find()
                        .projection(Projections.include(testKey))
                        .sort(Sorts.descending("_id"))
                        .first();
                if (doc == null || !doc.containsKey(testKey)) {
                    System.out.println("the doc from DB is empty");
                }

                Object value = doc.get(testKey);

                if (value instanceof Document) {
                    // Convert MongoDB Document to JsonObject
                    DnmObj = JsonParser.parseString(((Document) value).toJson()).getAsJsonObject();
                    callCamunda(DnmObj, DMN_KEYS.get(testKey), allResponses);
                }
            }
        } catch (Exception e) {
            ResponseEntity.badRequest().body("Error processing file: " + e.getMessage());
        }
        assert allResponses != null : "finalResult should not be null"; //to check if the object is null;
        return ResponseEntity.ok(allResponses.toString());
    }
    }
          /* if (camundaInput.size() > 0) {
                JsonObject electrolytesObject = new JsonObject(); // Declare once
                JsonObject electrolytesVariables = new JsonObject();
                boolean hasSodium = false, hasPotassium = false;
                JsonObject combinedElectrolytes = new JsonObject(); // To store combined sodium and potassium

                for (int i = 0; i < camundaInput.size(); i++) {
                    JsonObject testObject = camundaInput.get(i).getAsJsonObject();
                    JsonObject variablesObject = testObject.getAsJsonObject("variables");

                    // Iterate through all test names inside "variables"
                    for (String testName : variablesObject.keySet()) {
                        System.out.println("Processing test: " + testName);

                        if (testName.equals("sodium")) {
                            hasSodium = true;
                            JsonObject sodiumObject = new JsonObject();
                            sodiumObject.addProperty("value", variablesObject.get("sodium").getAsJsonObject().get("value").getAsDouble());
                            sodiumObject.addProperty("type", "Double");
                            combinedElectrolytes.add("sodium", sodiumObject);
                        }
                        if (testName.equals("potassium")) {
                            hasPotassium = true;
                            JsonObject potassiumObject = new JsonObject();
                            potassiumObject.addProperty("value", variablesObject.get("potassium").getAsJsonObject().get("value").getAsDouble());
                            potassiumObject.addProperty("type", "Double");
                            combinedElectrolytes.add("potassium", potassiumObject);
                        }

                        // Run DMN for individual tests (excluding sodium & potassium)
                        if (DMN_KEYS.containsKey(testName) && !testName.equals("sodium") && !testName.equals("potassium")) {
                            System.out.println("Invoking DMN for: " + testName);
                            callCamunda(testObject, DMN_KEYS.get(testName), allResponses);
                        }
                    }
                }
                // Run Electrolytes DMN only if both Sodium and Potassium exist
                if (hasSodium && hasPotassium) {
                    electrolytesObject = new JsonObject(); // Reset, instead of redeclaring
                    JsonObject variablesObject = new JsonObject();  // Create new "variables" object

                    // Ensure sodium and potassium exist before adding
                    if (combinedElectrolytes.has("sodium")) {
                        variablesObject.add("sodium", combinedElectrolytes.get("sodium"));
                    }
                    if (combinedElectrolytes.has("potassium")) {
                        variablesObject.add("potassium", combinedElectrolytes.get("potassium"));
                    }

                    electrolytesObject.add("variables", variablesObject);

                    // Debugging print
                    System.out.println("Final Electrolytes JSON: " + electrolytesObject);

                    // Run DMN
                    callCamunda(electrolytesObject, DMN_KEYS.get("electrolytes"), allResponses);

                }
                /*try (FileWriter file1 = new FileWriter("all_camunda_outputs.json")) {
                    file1.write(allResponses.toString());
                    finalResult = finalOutput(allResponses, camundaInput);
                    System.out.println("All Camunda outputs saved successfully!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("No test results found.");
            }

        } */






































































































































































































































































































































































































































































































































































































































































































































































































