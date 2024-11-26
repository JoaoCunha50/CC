# Define the source and output directories
SRC_DIR = src
BIN_DIR = bin
LIB_DIR = dependencies
LIBRARY = $(LIB_DIR)/json-simple-1.1.1.jar

# Java source files
SOURCES = $(SRC_DIR)/parser/Json_parser.java \
          $(SRC_DIR)/PDU/NetTask.java \
          $(SRC_DIR)/PDU/AlertFlow.java \
		  $(SRC_DIR)/utils/utils/TasksHandler.java \
		  $(SRC_DIR)/utils/utils/SeqManager.java \
          $(SRC_DIR)/nmsAgent.java \
          $(SRC_DIR)/nmsServer.java 

# Output class files
CLASSES = $(BIN_DIR)/AlertFlow.class \
          $(BIN_DIR)/nmsAgent.class \
          $(BIN_DIR)/nmsServer.class

# Compiler options
JAVAC = javac
JFLAGS = -cp ".:$(LIBRARY)" -d $(BIN_DIR)
DIRFLAGS = -d $(BIN_DIR)

# Default target to build the project
all: $(CLASSES)

$(BIN_DIR)/AlertFlow.class: $(SRC_DIR)/PDU/AlertFlow.java
	@mkdir -p $(BIN_DIR)
	$(JAVAC) $(DIRFLAGS) $(SRC_DIR)/PDU/AlertFlow.java

$(BIN_DIR)/nmsAgent.class: $(SRC_DIR)/nmsAgent.java
	@mkdir -p $(BIN_DIR)
	$(JAVAC) $(DIRFLAGS) $(SRC_DIR)/nmsAgent.java $(SRC_DIR)/PDU/NetTask.java $(SRC_DIR)/utils/utils/TasksHandler.java $(SRC_DIR)/utils/utils/SeqManager.java

$(BIN_DIR)/nmsServer.class: $(SRC_DIR)/nmsServer.java
	@mkdir -p $(BIN_DIR)
	$(JAVAC) $(JFLAGS) $(SRC_DIR)/nmsServer.java $(SRC_DIR)/PDU/NetTask.java $(SRC_DIR)/parser/Json_parser.java $(SRC_DIR)/utils/utils/SeqManager.java

# Clean target to remove compiled classes
clean:
	rm -rf $(BIN_DIR)

# Run the program
agent:
	@if [ -z "$(IP)" ]; then \
		echo "Usage: make agent IP=<server_ip>"; \
	else \
		java -cp "$(BIN_DIR):$(LIBRARY)" nmsAgent $(IP); \
	fi

server:
	java -cp "$(BIN_DIR):$(LIBRARY)" nmsServer
