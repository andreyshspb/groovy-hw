#!/usr/bin/env groovy

def parseArguments(args) {
    def arguments = args.clone()

    def linesLimit = 10
    def path = null

    if (arguments.size() == 0) {
        return [linesLimit, path]
    }

    def processingIndex = 0

    // Parse lines limit
    if (arguments[processingIndex].startsWith("-")) {
        linesLimit = -arguments[processingIndex].toInteger()
        processingIndex = 1
    }

    // Parse file path
    if (processingIndex < arguments.size()) {
        path = arguments[processingIndex]
    }

    return [linesLimit, path]
}

List<String> readFileToList(String path) {
    def file = (path == null) ? System.in.text : new File(path)
    return file.readLines()
}

def (linesLimit, path) = parseArguments(args)

def lines = readFileToList(path)
lines.eachWithIndex { element, index ->
    if (lines.size() - linesLimit <= index) {
        println element
    }
}
