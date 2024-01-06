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
