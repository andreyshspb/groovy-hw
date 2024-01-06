#!/usr/bin/env groovy

interface RecordAPI {
    int getSize()
    def getAt(int index)
    def getAt(String key)
    def getProperty(String property)
    boolean isCase(key)
    RecordStringView strview()
}

class RecordStringView implements RecordAPI {
    Record proxyableRecord

    RecordStringView(Record record) {
        proxyableRecord = record
    }

    @Override
    int getSize() {
        return proxyableRecord.getSize()
    }

    @Override
    def getAt(int index) {
        return proxyableRecord[index] as String
    }

    @Override
    def getAt(String key) {
        return proxyableRecord[key] as String
    }

    @Override
    def getProperty(String property) {
        def getterMethod = "get${property.capitalize()}"
        if (this.metaClass.respondsTo(this, getterMethod)) {
            if (property in this) {
                println "There is the conflict between <$property> csv-field and size property."
            }
            return this."$getterMethod"()
        }
        return proxyableRecord[property] as String
    }

    @Override
    boolean isCase(key) {
        return proxyableRecord.isCase(key)
    }

    @Override
    RecordStringView strview() {
        return proxyableRecord.strview()
    }
}

class Record implements RecordAPI {
    List<String> fields
    Map<String, Integer> keyToIndex

    Record(String raw) {
        fields = raw.split('\t')
        keyToIndex = [:]
    }

    Record(String raw, List<String> keys) {
        fields = raw.split('\t')
        keyToIndex = keys.withIndex().collectEntries { it }

        // Валидация
        if (keys.size() != keyToIndex.size()) {
            throw new IllegalArgumentException("Ключи должны быть уникальны.")
        }
        if (fields.size() != keys.size()) {
            throw new IllegalArgumentException("Количество ключей и количество полей должны совпадать.")
        }
    }

    @Override
    int getSize() {
        return fields.size()
    }

    @Override
    def getAt(int index) {
        if (fields[index].isLong()) {
            return fields[index].toLong()
        } else if (fields[index].isDouble()) {
            return fields[index].toDouble()
        }
        return fields[index]
    }

    @Override
    def getAt(String key) {
        return this[keyToIndex[key]]
    }

    @Override
    def getProperty(String property) {
        def getterMethod = "get${property.capitalize()}"
        if (this.metaClass.respondsTo(this, getterMethod)) {
            if (property in this) {
                println "There is the conflict between <$property> csv-field and size property."
            }
            return this."$getterMethod"()
        }
        return this[property]
    }

    @Override
    boolean isCase(key) {
        return keyToIndex.containsKey(key)
    }

    @Override
    RecordStringView strview() {
        return new RecordStringView(this)
    }
}

class Reader {
    String path
    boolean withHeader
    List<String> keys

    Reader(String path, boolean withHeader = true) {
        this.path = path
        this.withHeader = withHeader

        if (withHeader) {
            def reader = new FileReader(path)
            this.keys = reader.readLine()?.split("\t")
        }
    }

    void forEach(Closure closure) {
        def reader = new FileReader(path)

        if (withHeader) {
            reader.readLine()
        }

        def lastSize = null

        reader.readLines().each { line ->
            def record = withHeader ? new Record(line, keys) : new Record(line)

            if (lastSize != null && record.getSize() != lastSize) {
                throw new IllegalArgumentException("Количество полей в каждой строчке должны совпадать.")
            }
            lastSize = record.getSize()

            closure.call(record)
        }
    }

    void forEachCtx(int contextSize, Closure closure) {
        def context = []
        this.forEach { record ->
            closure.call(record, context)
            context = ([record] + context).take(contextSize)
        }
    }
}

// Asserts #1
{
 def record1 = new Record("Андрей Шеин\t22")
 def record2 = new Record("Маргарита Павловская")
 def record3 = new Record("")

 assert record1.getFields() == ["Андрей Шеин", "22"]
 assert record2.getFields() == ["Маргарита Павловская"]
 assert record3.getFields() == [""]

 assert record1.size == 2
 assert record2.size == 1
 assert record3.size == 1
}

// Asserts #2
{
 def record1 = new Record("abc\t123\t-24.56")

 assert record1[0].getClass() == String.class
 assert record1[1].getClass() == Long.class
 assert record1[2].getClass() == Double.class
}

// Asserts #3
{
 def record = new Record("Андрей Шеин\t22")

 assert record[1] == 22
 assert record.strview()[1] == "22"
 assert record.strview().strview()[1] == "22"
 assert record.strview().strview().strview()[1] == "22"
}

// Asserts #4
{
 def record1 = new Record("Андрей Шеин\t22", ["name", "age"])
 def record2 = new Record("Маргарита Павловская", ["name"])
 def record3 = new Record("", ["name"])

 assert record1.getFields() == ["Андрей Шеин", "22"]
 assert record2.getFields() == ["Маргарита Павловская"]
 assert record3.getFields() == [""]

 assert record1.size == 2
 assert record2.size == 1
 assert record3.size == 1

 assert record1[0] == "Андрей Шеин"
 assert record2[0] == "Маргарита Павловская"
 assert record3[0] == ""

 assert record1["name"] == "Андрей Шеин"
 assert record2["name"] == "Маргарита Павловская"
 assert record3["name"] == ""

 assert record1.name == "Андрей Шеин"
 assert record2.name == "Маргарита Павловская"
 assert record3.name == ""
}

// Asserts #5
{
 def record1 = new Record("Пальто\tL", ["product", "size"])

 assert record1.product == "Пальто"
 assert record1.strview().product == "Пальто"
 assert record1.size == 2
 assert record1.strview().size == 2
}

// Asserts #6
{
 def reader = new Reader("data.csv", /* withHeader= */ true)

 reader.forEach { record ->
  println record.name
 }

 reader.forEach { record ->
  println record.occupation
 }
}

// Asserts #7
{
 def reader = new Reader("data.csv", /* withHeader= */ true)

 reader.forEachCtx(/* contextSize= */ 2) { record, context ->
  println record.name
  println "---"
  context.each {
   println it.name
  }
  println "---"
 }
}
