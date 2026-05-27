#Requires -Version 5.1
<#
.SYNOPSIS
    Start, stop, and check demo servers for each Community Day KC 2026 code example.

.DESCRIPTION
    Port map:
      01  Java-Native ML (Weka)    :8082
      02  ONNX In-JVM              :8081
      03  Python FastAPI           :8000   Java Spring Client  :8080
      04  Spring AI (LLM)          :8006
      05  Docker Compose           Java pipeline :8084  Kafka :9092  Kafka UI :9090

.EXAMPLE
    .\demo.ps1 start 01       # Start example 01
    .\demo.ps1 start 03       # Start Python FastAPI + Java client
    .\demo.ps1 start all      # Start all examples
    .\demo.ps1 stop  03       # Stop example 03
    .\demo.ps1 stop  all      # Stop everything
    .\demo.ps1 status         # Check all ports
    .\demo.ps1 status 02      # Check example 02 only
#>

param(
    [Parameter(Position=0)]
    [ValidateSet("start","stop","status")]
    [string]$Action = "status",

    [Parameter(Position=1)]
    [ValidateSet("01","02","03","04","05","all")]
    [string]$Example = "all"
)

$ErrorActionPreference = "Continue"
$Root = $PSScriptRoot

# ---- Output helpers ---------------------------------------------------------
function Write-Ok($m)   { Write-Host "  [ok] $m" -ForegroundColor Green }
function Write-Warn($m) { Write-Host "  [!!] $m" -ForegroundColor Yellow }
function Write-Fail($m) { Write-Host "  [--] $m" -ForegroundColor Red }
function Write-Info($m) { Write-Host "  [..] $m" -ForegroundColor Cyan }
function Write-Head($m) { Write-Host "`n$m" -ForegroundColor White }

# ---- Port utilities ---------------------------------------------------------
function Get-PortOwner([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($conn) { return $conn.OwningProcess } else { return $null }
}

function Test-PortListening([int]$Port) {
    return $null -ne (Get-PortOwner $Port)
}

function Stop-ServicePort([int]$Port, [string]$Label) {
    $procId = Get-PortOwner $Port
    if ($procId) {
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Ok "Stopped $Label  (port $Port, PID $procId)"
    } else {
        Write-Warn "$Label not running on port $Port"
    }
}

function Show-PortStatus([string]$Label, [int]$Port) {
    if (Test-PortListening $Port) {
        Write-Ok  "$Label  :$Port  LISTENING"
    } else {
        Write-Fail "$Label  :$Port  not running"
    }
}

# ---- Open a service in a new PowerShell window ------------------------------
function Open-ServiceWindow([string]$Title, [string]$Dir, [string]$Cmd) {
    $shell = if (Get-Command pwsh -ErrorAction SilentlyContinue) { "pwsh" } else { "powershell" }
    $script = "Set-Location -LiteralPath '$Dir'; $Cmd"
    Start-Process $shell `
        -ArgumentList "-NoLogo", "-NoExit", "-Command", $script `
        -WorkingDirectory $Dir `
        -WindowStyle Normal
    Write-Ok "Opened window: $Title"
}

# ---- docker compose / docker-compose shim -----------------------------------
function Invoke-DockerCompose([string[]]$DcArgs) {
    Push-Location "$Root\05-full-pipeline"
    try {
        if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
            & docker-compose @DcArgs
        } else {
            & docker compose @DcArgs
        }
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Example 01 - Java-Native ML (Weka)  :8082
# =============================================================================

function Start-Example01 {
    Write-Head "01 - Java-Native ML (Weka)  port 8082"
    if (Test-PortListening 8082) { Write-Warn "Already running on :8082"; return }
    Open-ServiceWindow "01 Java-Native ML" "$Root\01-java-native-ml" "mvn spring-boot:run"
}

function Stop-Example01 {
    Write-Head "01 - Stop Java-Native ML"
    Stop-ServicePort 8082 "Java-Native ML"
}

# =============================================================================
# Example 02 - ONNX In-JVM  :8081
#   Requires fraud_model.onnx - run python-train-export first if missing
# =============================================================================

function Start-Example02 {
    Write-Head "02 - ONNX In-JVM  port 8081"
    if (Test-PortListening 8081) { Write-Warn "Already running on :8081"; return }

    $onnxFile = "$Root\02-onnx-in-jvm\java-onnx-inference\src\main\resources\models\fraud_model.onnx"
    if (-not (Test-Path $onnxFile)) {
        Write-Warn "ONNX model not found - opening one-time Python training window ..."
        Write-Info "Close the training window when done, then re-run: .\demo.ps1 start 02"
        Open-ServiceWindow "02 ONNX Training (one-time)" `
            "$Root\02-onnx-in-jvm\python-train-export" `
            "pip install -r requirements.txt -q; python train_export.py; Write-Host 'Training complete. Close this window then re-run: .\demo.ps1 start 02' -ForegroundColor Green"
        return
    }

    Open-ServiceWindow "02 ONNX In-JVM" "$Root\02-onnx-in-jvm\java-onnx-inference" "mvn spring-boot:run"
}

function Stop-Example02 {
    Write-Head "02 - Stop ONNX In-JVM"
    Stop-ServicePort 8081 "ONNX In-JVM"
}

# =============================================================================
# Example 03 - Model as a Service  :8000 (Python) + :8080 (Java)
# =============================================================================

function Start-Example03 {
    Write-Head "03 - Model as a Service  port 8000 (Python FastAPI) + 8080 (Java)"

    if (Test-PortListening 8000) {
        Write-Warn ":8000 already in use - skipping Python service"
    } else {
        Open-ServiceWindow "03 Python FastAPI" `
            "$Root\03-model-as-service\python-ml-service" `
            "uvicorn main:app --reload --port 8000"
        Write-Info "Waiting 3 s for FastAPI to boot ..."
        Start-Sleep 3
    }

    if (Test-PortListening 8080) {
        Write-Warn ":8080 already in use - skipping Java client"
    } else {
        Open-ServiceWindow "03 Java Spring Client" `
            "$Root\03-model-as-service\java-spring-client" `
            "mvn spring-boot:run"
    }
}

function Stop-Example03 {
    Write-Head "03 - Stop Model as a Service"
    Stop-ServicePort 8000 "Python FastAPI"
    Stop-ServicePort 8080 "Java Spring Client"
}

# =============================================================================
# Example 04 - Spring AI (LLM Fraud Explainer)  :8006
# =============================================================================

function Start-Example04 {
    Write-Head "04 - Spring AI (LLM Fraud Explainer)  port 8006"
    if (Test-PortListening 8006) { Write-Warn "Already running on :8006"; return }

    if (-not $env:OPENAI_API_KEY -and -not $env:ANTHROPIC_API_KEY) {
        Write-Warn "No LLM API key found in environment"
        Write-Info "  Set OPENAI_API_KEY or ANTHROPIC_API_KEY before calling /api/fraud/explain"
        Write-Info "  Or configure Ollama in 04-spring-ai\src\main\resources\application.properties"
        Write-Info "Starting anyway ..."
    }

    Open-ServiceWindow "04 Spring AI" "$Root\04-spring-ai" "mvn spring-boot:run"
}

function Stop-Example04 {
    Write-Head "04 - Stop Spring AI"
    Stop-ServicePort 8006 "Spring AI"
}

# =============================================================================
# Example 05 - Full Pipeline (Docker Compose)
#   :8084 Java pipeline  :8000 ML service  :9092 Kafka  :9090 Kafka UI
# =============================================================================

function Start-Example05 {
    Write-Head "05 - Full Pipeline (Docker Compose)"

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Fail "Docker not found - install Docker Desktop and try again"
        return
    }

    Invoke-DockerCompose "up", "--build", "-d"
    Write-Ok "Stack started"
    Write-Info "  Java pipeline  ->  http://localhost:8084/api/pipeline/status"
    Write-Info "  Kafka UI       ->  http://localhost:9090"
    Write-Info "  ML service     ->  http://localhost:8000/docs"
    Write-Info ""
    Write-Info "  Send test transaction:"
    Write-Info "    curl http://localhost:8084/api/pipeline/test/send?amount=1800&merchantCategory=CRYPTO"
    Write-Info "  Tail logs:"
    Write-Info "    docker-compose logs -f java-pipeline   (run from 05-full-pipeline folder)"
}

function Stop-Example05 {
    Write-Head "05 - Stop Full Pipeline"

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Fail "Docker not found"
        return
    }

    Invoke-DockerCompose "down"
    Write-Ok "Stack stopped"
}

# =============================================================================
# Status - check all ports
# =============================================================================

function Show-ExampleStatus([string]$Ex) {
    switch ($Ex) {
        "01" {
            Write-Head "01 - Java-Native ML"
            Show-PortStatus "Java-Native ML (Weka)" 8082
        }
        "02" {
            Write-Head "02 - ONNX In-JVM"
            Show-PortStatus "ONNX In-JVM" 8081
        }
        "03" {
            Write-Head "03 - Model as a Service"
            Show-PortStatus "Python FastAPI" 8000
            Show-PortStatus "Java Spring Client" 8080
        }
        "04" {
            Write-Head "04 - Spring AI"
            Show-PortStatus "Spring AI" 8006
        }
        "05" {
            Write-Head "05 - Full Pipeline (Docker)"
            Show-PortStatus "Java Pipeline" 8084
            Show-PortStatus "ML Service (Docker)" 8000
            Show-PortStatus "Kafka Broker" 9092
            Show-PortStatus "Kafka UI" 9090
        }
        "all" {
            foreach ($e in @("01","02","03","04","05")) { Show-ExampleStatus $e }
        }
    }
}

# =============================================================================
# Dispatch
# =============================================================================

if ($Action -eq "status") {
    Show-ExampleStatus $Example
} else {
    $targets = if ($Example -eq "all") { @("01","02","03","04","05") } else { @($Example) }

    foreach ($t in $targets) {
        if ($Action -eq "start") {
            switch ($t) {
                "01" { Start-Example01 }
                "02" { Start-Example02 }
                "03" { Start-Example03 }
                "04" { Start-Example04 }
                "05" { Start-Example05 }
            }
        } else {
            switch ($t) {
                "01" { Stop-Example01 }
                "02" { Stop-Example02 }
                "03" { Stop-Example03 }
                "04" { Stop-Example04 }
                "05" { Stop-Example05 }
            }
        }
    }
}

Write-Host ""
