# From Java to AI — Working Code Examples
**Community Day KC 2026 · Surya Rao Rayarao**

Each folder is a numbered, self-contained working example matching a slide in the talk.

## Examples

| # | Example | Pattern | Technologies |
|---|---------|---------|--------------|
| [01](./01-model-as-service/) | Model as a Service | Java calls Python ML over REST | Spring Boot, FastAPI, WebClient |
| [02](./02-onnx-in-jvm/) | ONNX In-JVM | Train in Python, infer inside the JVM | ONNX Runtime for Java, scikit-learn |
| [03](./03-java-native-ml/) | Java-Native ML | Train and serve entirely in Java | Spring Boot, Weka, RandomForest |
| [04](./04-feature-engineering/) | Feature Engineering | Java feature pipeline patterns | Spring Boot, in-memory velocity store |
| [05](./05-full-pipeline/) | Full ML Pipeline | End-to-end event-driven fraud detection | Kafka, ONNX Runtime, Docker Compose |
| [06](./06-spring-ai/) | Spring AI | LLM-powered fraud explanation with structured output | Spring AI 1.0, OpenAI / Anthropic / Ollama |

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17+ | All Java examples |
| Maven | 3.8+ | Build tool |
| Python | 3.10+ | Examples 01, 02 (Python side), 05 |
| Docker Desktop | Latest | Example 05 only |
| OpenAI API key | — | Example 06 (or swap to Anthropic/Ollama — see README) |

## Quick Start

```bash
# Clone and enter the code directory
cd code/

# Run example 01 (start Python service first)
cd 01-model-as-service/python-ml-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000

# In a new terminal — run the Java client
cd 01-model-as-service/java-spring-client
mvn spring-boot:run
# POST to http://localhost:8080/api/fraud/demo
```

## Domain

All examples use **fraud detection in financial transactions** — the core problem
from the FinTech use case in the talk. Each example solves the same problem
using a different integration pattern, so you can compare them side by side.
