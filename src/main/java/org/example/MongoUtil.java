package org.example;
import com.mongodb.client.*;

public class MongoUtil {

    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DATABASE_NAME = "women";

    private static MongoDatabase database;

    static {try{
        MongoClient mongoClient = MongoClients.create(CONNECTION_STRING);
        database = mongoClient.getDatabase(DATABASE_NAME);
    } catch (Exception e) {
        System.err.println("⚠ MongoDB Connection Failed: " + e.getMessage());
    }
    }

    public static MongoDatabase getDatabase() {
        return database;
    }
}

