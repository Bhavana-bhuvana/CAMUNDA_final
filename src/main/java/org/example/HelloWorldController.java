package org.example;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
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
import static java.lang.System.*;
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
    static
    ObjectId id;
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
            id = textExtractionService.extractText(file);
            // Step 3: Call Camunda API and return decision result
            JsonObject DnmObj = new JsonObject();
            allResponses = new JsonArray();
            MongoCollection<Document> collection = MongoUtil.getDatabase().getCollection("user");
                Document doc = collection.find(Filters.eq("_id", id)).first();
                if (doc == null) {
                    return ResponseEntity.badRequest().body("Document not found for ID: " + id.toHexString());
                }
                for (String testKey : DMN_KEYS.keySet()) {
                    if (!doc.containsKey(testKey)) {
                        out.println("Field '" + testKey + "' missing in document");
                        continue;
                    }
                    Object value = doc.get(testKey);
                    out.println("value"+value+"going inside");
                    out.println("if (value instanceof Document)"+(value instanceof Document));
                    if (value instanceof Document) {
                         DnmObj = JsonParser.parseString(((Document) value).toJson()).getAsJsonObject();
                        out.println("DnmObj"+DnmObj+"going inside");
                        callCamunda(DnmObj, DMN_KEYS.get(testKey), allResponses);
                    }
                }
        } catch (Exception e) {
            ResponseEntity.badRequest().body("Error processing file: " + e.getMessage());
        }
        JsonObject finalResult = null;
        try (FileWriter file1 = new FileWriter("all_camunda_outputs.json")) {
            file1.write(allResponses.toString());
            finalResult = finalOutput(allResponses, id);
            out.println("All Camunda outputs saved successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert allResponses != null : "finalResult should not be null"; //to check if the object is null;
        return ResponseEntity.ok(finalResult.toString());
    }
    }






































































































































































































































































































































































































































































































































































































































































































































































































