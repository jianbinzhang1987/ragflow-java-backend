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

### 1. External Providers (LLM/Embedding)
- Open `src/main/resources/application.yml`
- Set `embedding.provider: openai` and `llm.provider: openai`
- Fill in your `api-key`.

### 2. File Upload Limits
You can configure the maximum file size in `application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
rag:
  max-file-size: 10MB
```
These values are synchronized with the frontend.

### 3. System Configuration API
The system provides a configuration endpoint for the frontend to sync settings:
- `GET /api/v1/system/config`
- Returns: `{"code":0,"data":{"maxFileSize":"10MB"}}`

## Core Logic & Features

- **Vector Store**: Uses a **Simulated Faiss** implementation (`FaissVectorStore`) that persists vectors to `./data/index/<collection>.faiss`.
- **System Maintenance**: Automatically filters system hidden files (like `.sys_init`) from user-facing file lists.
- **Global Exception Handling**: Provides user-friendly error messages for common issues like file size limit exceeded.

## Project Structure
- `pipeline`: Ingest (Parse/Chunk) and Query (Context/Prompt) logic.
- `vectorstore`: Vector storage abstraction.
- `llm` / `embedding`: Pluggable AI clients.
- `service`: Orchestration.
- `common`: Global response and exception handling.
- `controller`: API endpoints including `SystemController` for dynamic config.

