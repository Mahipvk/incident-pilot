# IncidentPilot

**An AI agent that triages production incidents for a file-transfer platform.** It reads the alert, checks service health, searches the logs, looks up runbooks and past postmortems (RAG), and returns a structured root-cause report with evidence, next steps and an audit trail of every step it took.

Built in **Java 21 + Spring Boot 3 + Spring AI**, with **pgvector** for retrieval, **Ollama** for a fully local LLM (no data leaves the machine), an **MCP server** that exposes the same tools to any AI client, a **PII/secret redaction guardrail**, and an **evaluation harness** that scores the agent against labelled incidents.

> All platform data, logs, runbooks and incidents in this repo are synthetic.

---

## Why this exists

On-call engineers spend the first 30-60 minutes of an incident doing the same thing: check dashboards, grep logs across services, find the runbook, remember whether it happened before. IncidentPilot does that first pass automatically and shows its work, so the human starts from a cited hypothesis instead of a blank screen.

## Architecture

```mermaid
flowchart LR
    UI[Web UI / REST API] --> T[TriageService<br/>Spring AI ChatClient]
    T -->|redacted alert| LLM[(Ollama LLM<br/>qwen2.5:7b)]
    LLM -->|tool calls| Tools[OpsTools]
    Tools --> H[getServiceHealth]
    Tools --> L[searchLogs]
    Tools --> R[searchRunbooks]
    Tools --> P[findSimilarIncidents]
    R & P --> V[(pgvector<br/>embeddings)]
    I[KnowledgeIngestor<br/>runbooks + postmortems] -->|chunk + embed| V
    T --> Rep[Typed TriageReport<br/>+ tool audit trail]
    MCP[MCP server /sse] --> Tools
    E[EvalService] --> T
```

| Piece | What it does | File |
|---|---|---|
| Agent | System prompt + tool calling + structured JSON output | `triage/TriageService.java` |
| Tools | Health, log search, runbook search, similar incidents. Each call is traced. | `ops/OpsTools.java` |
| RAG ingestion | Splits markdown runbooks/postmortems into chunks, embeds, stores in pgvector | `knowledge/KnowledgeIngestor.java` |
| RAG retrieval | Similarity search with metadata filter (runbook vs incident) | `knowledge/KnowledgeBase.java` |
| Guardrail | Masks emails, passwords, tokens, AWS keys, SSNs, card numbers before the LLM sees text | `guard/Redactor.java` |
| Evals | Replays every incident snapshot and reports accuracy, citation rate, latency | `eval/EvalService.java` |
| MCP | Publishes the same tools over Model Context Protocol | `config/AppConfig.java` |
| Data | 6 incident snapshots, 6 runbooks, 5 postmortems | `src/main/resources/` |

## Run it (Windows, PowerShell)

Needs **Docker Desktop** and ~16 GB RAM (8 GB works with the smaller model below). Nothing else: Java and Maven run inside Docker.

```powershell
git clone https://github.com/<you>/incidentpilot; cd incidentpilot
docker compose up --build
```

First start downloads the models (~5 GB), so allow 10-20 minutes. When the log says `Started IncidentPilotApplication`, open **http://localhost:8080**.

Low on RAM? Use a smaller model:

```powershell
$env:CHAT_MODEL="qwen2.5:3b"; docker compose up --build
```

### Mac (Apple Silicon)

Install the Ollama app from ollama.com and open it (it runs natively and uses the Mac GPU), then in Terminal:

```bash
docker compose -f docker-compose.mac.yml up --build
```

## Use it

- **UI:** pick an incident, click *Triage this incident*. Click *Run evals* to score all six.
- **API:**
  ```powershell
  irm localhost:8080/api/triage -Method Post -ContentType application/json -Body '{"snapshotId":"disk-full"}'
  irm localhost:8080/api/evals -Method Post
  ```
- **MCP:** any MCP client can connect to `http://localhost:8080/sse` and call the four ops tools directly. Easiest test: `npx @modelcontextprotocol/inspector`, then connect to that URL.

## Evaluation results

Fill this table with **your own** numbers from the *Run evals* button. Results vary by model and hardware.

| Model | Accuracy | Cited a source | Avg time / incident | Notes |
|---|---|---|---|---|
| qwen2.5:7b | _/6 | _% | _s | |
| qwen2.5:3b | _/6 | _% | _s | |

## Design decisions (good interview material)

- **Local model by default.** Incident data in banks and other regulated firms usually can't go to a public API. Ollama keeps everything on the machine. Spring AI makes swapping in Claude, OpenAI or AWS Bedrock a dependency + config change.
- **Snapshots instead of live systems.** Each incident is a frozen set of health, logs and alert. This makes the agent testable and the evals repeatable.
- **Typed output.** The model must return JSON matching `TriageReport`. If it doesn't, the service falls back safely instead of crashing.
- **Audit trail.** Every tool call is recorded and shown, so an engineer can check *why* the agent concluded something.
- **Redaction at the boundary.** Both the alert and every log line returned by a tool go through the redactor before reaching the model.
- **One snapshot has no matching postmortem** (`queue-backlog`), to test that the agent reasons from logs and runbooks instead of copying a past incident.

## Roadmap

- [ ] Add a Claude / Bedrock profile and compare eval scores against the local model
- [ ] Ingest alerts from Kafka instead of the UI
- [ ] OpenTelemetry tracing of every LLM and tool call
- [ ] Angular front end
- [ ] Deploy to AWS (ECS or EKS) with Terraform/CloudFormation
- [ ] Add a "hallucination check" eval: every quoted evidence line must exist in the snapshot logs
