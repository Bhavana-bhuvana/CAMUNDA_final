package org.example.service;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.sourceforge.tess4j.TesseractException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.example.MongoUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import net.sourceforge.tess4j.Tesseract;
import com.opencsv.CSVReader;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.mongodb.client.*;
import static org.example.service.CamundaService.startCamundaAndRunDMN;

@Service
public class TextExtractionService {
    JsonObject groupedElectrolytes = new JsonObject();
    // To store sodium and potassium together
    private final MongoCollection<Document> collection;
    public TextExtractionService() {
        this.collection = MongoUtil.getDatabase().getCollection("user");
    }
    public ObjectId extractText(MultipartFile file) throws Exception {

        String extractedText = extractTextFromFile(file);
        System.out.println("after text extration  to  text function starting");

        ObjectId id = prepareCamundaInput(extractedText);
        return id;

    }

    public static Map<String, String> extractTestValues(String text) {
        Map<String, String> extractedTests = new LinkedHashMap<>();
        Map<String, List<String>> requiredTests = new HashMap<>();

        requiredTests.put("creatinine", Arrays.asList("creatinine", "serum creatinine"));
        requiredTests.put("bun", Arrays.asList("bun", "blood urea nitrogen"));
        requiredTests.put("sodium", Arrays.asList("sodium", "serum sodium"));
        requiredTests.put("potassium", Arrays.asList("potassium", "serum potassium"));
        requiredTests.put("uacr", Arrays.asList("uacr", "urine albumin-to-creatinine ratio", "urine acr", "albumin creatinine ratio", "urine albumin/creatinine"));

        // Improved regex: Captures variations, ignores case sensitivity
        Pattern pattern = Pattern.compile("(?i)([A-Za-z /-]+?)[:=]?\\s*([0-9]+\\.?[0-9]*)\\s*(mg/dL|mmol/L|g/L|mg/g|%)?");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String testName = matcher.group(1).trim().toLowerCase();
            String testValue = matcher.group(2).trim();
            String unit = matcher.group(3) != null ? matcher.group(3) : ""; // Handle missing units
            // Ignore BUN/Creatinine Ratio to avoid incorrect values
            if (testName.contains("ratio") && !testName.toLowerCase().contains("albumin")) {
                continue;
            }
            for (Map.Entry<String, List<String>> entry : requiredTests.entrySet()) {
                for (String alias : entry.getValue()) {
                    // Improved matching: Handles multi-word phrases better
                    if (testName.replaceAll("[^a-zA-Z]", " ").contains(alias.toLowerCase())) {
                        extractedTests.put(entry.getKey(), testValue + " " + unit);
                        break;
                    }
                }
            }
        }

        return extractedTests;
    }

    public static String extractTestCases(String text) {
        String regex = "(?i)\\b(creatinine|serum creatinine|sodium|potassium|chloride|electrolytes|blood urea nitrogen|bun|glomerular filtration rate|gfr|urine albumin-to-creatinine ratio|uacr|albumin creatinine ratio)\\b\\s*[:=-]?\\s*(\\d{1,3}(?:\\.\\d{1,2})?)";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);

        StringBuilder resultBuilder = new StringBuilder();

        while (matcher.find()) {
            String testName = matcher.group(1).trim();
            String result = matcher.group(2).trim();
            resultBuilder.append(testName).append(": ").append(result).append("\n");
        }
        return resultBuilder.toString().trim();
    }
    private String extractTextFromFile(MultipartFile file) throws Exception {
        System.out.println("inside extract text function which deals with different types of file");
        String fileName = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        String text = "";
        String extracted = "";
        if (fileName.endsWith(".pdf")) {

            try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(file.getInputStream())) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                text = stripper.getText(document).toLowerCase();
                System.out.println(text);
                extracted = extractTestValues(text).toString();
                System.out.println("extracted results:" + extracted);
            }
        } else if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".webp")) {
            try {

                System.out.println("Inside image text extraction block of code");
                File imageFile = File.createTempFile("uploaded_", file.getOriginalFilename());
                Files.copy(file.getInputStream(), imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                Tesseract tesseract = new Tesseract();
                text = tesseract.doOCR(imageFile).toLowerCase();
                extracted = extractTestCases(text).toString();
            } catch (TesseractException e) {
                e.printStackTrace();
                return "Error extracting text from image.";
            }
        } else if (fileName.endsWith(".csv")) {
            try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
                String[] line;
                while ((line = reader.readNext()) != null) {
                    extracted+= String.join(" ", line) + "\n";
                }
            }
        } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            try (InputStream fis = file.getInputStream();
                 Workbook workbook = WorkbookFactory.create(fis)) {
                for (Sheet sheet : workbook) {
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            extracted += cell.toString() + " ";
                        }
                        extracted += "\n";
                    }
                }
            }
        } else {
            throw new Exception("Unsupported file format.");
        }

        return extracted.toString();
    }

    public JsonObject createTestJson(String testName, double testScore) {
        startCamundaAndRunDMN();
        JsonObject valueObject = new JsonObject();
        valueObject.addProperty("value", testScore);
        valueObject.addProperty("type", "Double");


        if (testName.equalsIgnoreCase("sodium") || testName.equalsIgnoreCase("potassium")) {
            groupedElectrolytes.add(testName, valueObject);
            return null;
        } else {
            // Normal Test Case
            JsonObject variableObject = new JsonObject();
            variableObject.add(testName, valueObject);

            JsonObject testObject = new JsonObject();
            testObject.add("variables", variableObject);

            return testObject;
        }
    }
    public ObjectId prepareCamundaInput(String text) {
        System.out.println("in");
        ObjectId id = new ObjectId();  // ← This is creating a completely NEW ObjectId every time you run it
        Document baseDoc = new Document("_id", id);
        System.out.println("onj_id" + id);
        collection.insertOne(baseDoc);
        groupedElectrolytes = new JsonObject();
        text = text.toLowerCase();
        JsonObject valueObject1 = new JsonObject();
        System.out.println("extrated text" + text);
        JsonArray testResultsArray = new JsonArray();
        String regex = "(?i)\\b(creatinine|creatinine serum|creatinine blood|creatinine level|serum creatinine|bun|blood urea nitrogen|sodium|sodium serum|serum sodium|na|potassium|serum potassium|k|gfr|glomerular filtration rate|gfr creatinine|egfr|uacr|albumin creatinine ratio)\\b\\s*[:=\\-]?\\s*(\\d{1,3}(?:\\.\\d{1,2})?)";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String testName = matcher.group(1).toLowerCase();
            System.out.println("test: " + testName);
            double testValue = Double.parseDouble(matcher.group(2));
            // Standardizing test names
            if (testName.matches("bun|blood urea nitrogen|bun/creatinine")) {
                testName = "bun";
            } else if (testName.matches("creatinine|creatinine serum|creatinine blood|creatinine level|serum creatinine")) {
                testName = "creatinine";
            } else if (testName.matches("gfr|glomerular filtration rate|gfr creatinine|egfr")) {
                testName = "gfr";
            } else if (testName.matches("uacr|albumin creatinine ratio")) {
                testName = "uacr";
            } else if (testName.matches("sodium|sodium serum|serum sodium|na")) {
                testName = "sodium";

            } else if (testName.matches("potassium|serum potassium|k")) {
                testName = "potassium";
            }
            valueObject1 = createTestJson(testName, testValue);
            if (valueObject1 != null) {
                testResultsArray.add(valueObject1);
                Document valueDoc = Document.parse(valueObject1.toString());
                collection.updateOne(
                        new Document("_id", id),
                        new Document("$set", new Document(testName, valueDoc))
                );
            }
            System.out.println("final Extracted Test Results: " + testResultsArray);

        }
        if (groupedElectrolytes.size() > 0) {
            JsonObject electrolytesObject = new JsonObject();
            electrolytesObject.add("variables", groupedElectrolytes);
            testResultsArray.add(electrolytesObject);

            Document electrolytesDoc = Document.parse(electrolytesObject.toString());
            collection.updateOne(
                    new Document("_id", id),
                    new Document("$set", new Document("electrolytes", electrolytesDoc))
            );
            System.out.println("Electrolytes inserted: " + groupedElectrolytes.toString());
        }
        return id;
    }
}