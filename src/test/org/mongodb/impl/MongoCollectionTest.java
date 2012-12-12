/**
 * Copyright (c) 2008 - 2012 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.mongodb.impl;

import org.bson.BSONReader;
import org.bson.BSONWriter;
import org.bson.types.ObjectId;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mongodb.MongoCollection;
import org.mongodb.MongoCursor;
import org.mongodb.MongoDatabase;
import org.mongodb.MongoDocument;
import org.mongodb.MongoQueryFilterDocument;
import org.mongodb.MongoUpdateOperationsDocument;
import org.mongodb.ServerAddress;
import org.mongodb.command.DropDatabaseCommand;
import org.mongodb.operation.MongoFind;
import org.mongodb.operation.MongoFindAndUpdate;
import org.mongodb.operation.MongoInsert;
import org.mongodb.operation.MongoRemove;
import org.mongodb.result.InsertResult;
import org.mongodb.serialization.BsonSerializationOptions;
import org.mongodb.serialization.Serializer;
import org.mongodb.serialization.PrimitiveSerializers;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnit4.class)
public class MongoCollectionTest {
    // TODO: this is untenable
    private static SingleServerMongoClient mongoClient;
    private static final String DB_NAME = "MongoCollectionTest";
    private static MongoDatabase mongoDatabase;

    @BeforeClass
    public static void setUpClass() throws UnknownHostException {
        mongoClient = new SingleServerMongoClient(new ServerAddress());
        new DropDatabaseCommand(mongoClient, DB_NAME).execute();
        mongoDatabase = mongoClient.getDatabase(DB_NAME);
        new DropDatabaseCommand(mongoClient, DB_NAME).execute();
    }

    @AfterClass
    public static void tearDownClass() {
        mongoClient.close();
    }

    @Before
    public void setUp() throws UnknownHostException {
    }

    @Test
    public void testInsertMultiple() {
        final MongoCollection<MongoDocument> collection = mongoDatabase.getCollection("insertMultiple");

        final List<MongoDocument> documents = new ArrayList<MongoDocument>();
        for (int i = 0; i < 10; i++) {
            final MongoDocument doc = new MongoDocument("_id", i);
            documents.add(doc);
        }

        final InsertResult res = collection.insert(new MongoInsert<MongoDocument>(documents));
        assertEquals(10, collection.count());
        assertNotNull(res);
    }

    @Test
    public void testRemove() {
        final MongoCollection<MongoDocument> collection = mongoDatabase.getCollection("insertMultiple");

        final List<MongoDocument> documents = new ArrayList<MongoDocument>();
        for (int i = 0; i < 10; i++) {
            final MongoDocument doc = new MongoDocument("_id", i);
            documents.add(doc);
        }

        collection.insert(new MongoInsert<MongoDocument>(documents));
        collection.remove(new MongoRemove(new MongoQueryFilterDocument("_id", 5)));
        assertEquals(9, collection.count());
    }

    @Test
    public void testFind() {
        final MongoCollection<MongoDocument> collection = mongoDatabase.getCollection("find");

        for (int i = 0; i < 101; i++) {
            final MongoDocument doc = new MongoDocument("_id", i);
            collection.insert(new MongoInsert<MongoDocument>(doc));
        }

        final MongoCursor<MongoDocument> cursor = collection.find(new MongoFind(new MongoQueryFilterDocument()));
        try {
            while (cursor.hasNext()) {
                final MongoDocument cur = cursor.next();
                System.out.println(cur);
            }
        } finally {
            cursor.close();
        }
    }

    @Test
    public void testCount() {
        final MongoCollection<MongoDocument> collection = mongoDatabase.getCollection("count");

        for (int i = 0; i < 11; i++) {
            final MongoDocument doc = new MongoDocument("_id", i);
            collection.insert(new MongoInsert<MongoDocument>(doc));
        }

        long count = collection.count(new MongoFind(new MongoQueryFilterDocument()));
        assertEquals(11, count);

        count = collection.count(new MongoFind(new MongoQueryFilterDocument("_id", 10)));
        assertEquals(1, count);
    }

    @Test
    public void testFindAndUpdate() {
        final MongoCollection<MongoDocument> collection = mongoDatabase.getCollection("findAndUpdate");

        final MongoDocument doc = new MongoDocument("_id", 1);
        doc.put("x", true);
        collection.insert(new MongoInsert<MongoDocument>(doc));

        final MongoDocument newDoc = collection.findAndUpdate(new MongoFindAndUpdate().
                where(new MongoQueryFilterDocument("x", true)).
                updateWith(new MongoUpdateOperationsDocument("$set", new MongoDocument("x", false))));


        assertNotNull(newDoc);
        assertEquals(doc, newDoc);
    }

    @Test
    public void testFindAndUpdateWithGenerics() {
        final PrimitiveSerializers primitiveSerializers = PrimitiveSerializers.createDefault();
        final MongoCollection<Concrete> collection = mongoDatabase.getTypedCollection("findAndUpdateWithGenerics",
                primitiveSerializers, new ConcreteSerializer());

        final Concrete doc = new Concrete(new ObjectId(), true);
        collection.insert(new MongoInsert<Concrete>(doc));

        final Concrete newDoc = collection.findAndUpdate(new MongoFindAndUpdate().
                where(new MongoQueryFilterDocument("x", true)).
                updateWith(new MongoUpdateOperationsDocument("$set", new MongoDocument("x", false))));


        assertNotNull(newDoc);
        assertEquals(doc, newDoc);
    }

}

class Concrete {
    ObjectId id;
    boolean x;

    public Concrete(final ObjectId id, final boolean x) {
        this.id = id;
        this.x = x;
    }

    public Concrete() {
    }

    @Override
    public String toString() {
        return "Concrete{" + "id=" + id + ", x='" + x + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Concrete concrete = (Concrete) o;

        if (x != concrete.x) return false;
        if (!id.equals(concrete.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (x ? 1 : 0);
        return result;
    }
}

class ConcreteSerializer implements Serializer<Concrete> {

    @Override
    public void serialize(final BSONWriter bsonWriter, final Concrete value, final BsonSerializationOptions options) {
        bsonWriter.writeStartDocument();
        {
            bsonWriter.writeObjectId("_id", value.id);
            bsonWriter.writeBoolean("x", value.x);
        }
        bsonWriter.writeEndDocument();
    }

    @Override
    public Concrete deserialize(final BSONReader reader, final BsonSerializationOptions options) {
        final Concrete c = new Concrete();
        reader.readStartDocument();
        {
            c.id = reader.readObjectId("_id");
            c.x = reader.readBoolean("x");
        }
        reader.readEndDocument();
        return c;
    }

    @Override
    public Class<Concrete> getSerializationClass() {
        return Concrete.class;
    }
}

