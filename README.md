# RAGFlow Java Backend

A minimal, runnable RAG backend using Spring Boot 3 + Java 17 + H2 + Local Vector Store (Faiss-like).

## Requirements
- Java 17+
- Maven 3.6+

## Quick Start

### 1. Build & Run
```bash
mvn clean package
mvn spring-boot:run
```
Server will start at `http://localhost:8081`.

### 2. H2 Console
Database console is available at:
- URL: `http://localhost:8081/h2-console`
- JDBC URL: `jdbc:h2:file:./data/db/ragflow`
- User: `sa`
- Password: (empty)

### 3. API Examples

#### Upload Document
```bash
curl -F "file=@/path/to/test.txt" -F "collection=default" http://localhost:8081/api/v1/docs/upload
```
Response:
```json
{"code":0,"data":{"docId":1,"fileName":"test.txt","status":"UPLOADED"}}
```

#### Trigger Indexing
```bash
curl -X POST http://localhost:8081/api/v1/docs/1/index
```
Response:
```json
{"code":0,"data":{"docId":1,"chunkCount":5,"status":"INDEXED"}}
```

#### RAG Query
```bash
curl -X POST http://localhost:8081/api/v1/chat/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What does this document say about X?",
    "collection": "default",
    "topK": 3
  }'
```

#### Vector Search Only
```bash
curl -X POST http://localhost:8081/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Summary",
    "topK": 2
  }'
```

## Configuration

Use `application.yml` to switch between Mock and OpenAI providers.

### To use OpenAI:
1. Open `src/main/resources/application.yml`
2. Set `embedding.provider: openai` and `llm.provider: openai`
3. Fill in your `api-key`.

### Vector Store
By default, the system uses a **Simulated Faiss** implementation (`FaissVectorStore`) that persists vectors to `./data/index/<collection>.faiss`. This runs purely on Java without native dependencies, satisfying the requirements for a "compilable anywhere" solution.

## Project Structure
- `pipeline`: Ingest (Parse/Chunk) and Query (Context/Prompt) logic.
- `vectorstore`: Vector storage abstraction.
- `llm` / `embedding`: Pluggable AI clients.
- `service`: Orchestration.

