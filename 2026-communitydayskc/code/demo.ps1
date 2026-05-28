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
# Python ML Service  :8000  (shared across examples 01-04)
# =============================================================================

function Start-PythonService {
    if (Test-PortListening 8000) {
        Write-Ok "Python ML service already running on :8000"
        return
    }
    $pyDir = "$Root\03-model-as-service\python-ml-service"
    if (-not (Test-Path "$pyDir\.venv\Scripts\uvicorn.exe")) {
        Write-Info "Setting up Python venv ..."
        Push-Location $pyDir
        try {
            if (-not (Test-Path "$pyDir\.venv")) { python -m venv .venv }
            & "$pyDir\.venv\Scripts\pip" install -r requirements.txt -q
            Write-Ok "Python venv ready"
        } finally {
            Pop-Location
        }
    }
    Open-ServiceWindow "Python ML Service :8000" `
        $pyDir `
        "& '.venv\Scripts\python.exe' -m uvicorn main:app --reload --port 8000"

    Write-Info "Waiting for Python ML service to be ready ..."
    $deadline = (Get-Date).AddSeconds(30)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep 2
        try {
            $resp = Invoke-RestMethod "http://localhost:8000/health" -TimeoutSec 2 -ErrorAction Stop
            if ($resp.model_loaded -eq $true) { $ready = $true; break }
        } catch {}
    }
    if ($ready) { Write-Ok "Python ML service ready (model loaded)" }
    else         { Write-Warn "Python ML service did not respond within 30 s - continuing anyway" }
}

function Stop-PythonService {
    Stop-ServicePort 8000 "Python ML service"
}

# =============================================================================
# Example 01 - Java-Native ML (Weka)  :8082
# =============================================================================

function Start-Example01 {
    Write-Head "01 - Java-Native ML (Weka)  port 8082"
    Start-PythonService
    if (Test-PortListening 8082) { Write-Warn "Already running on :8082"; return }
    Open-ServiceWindow "01 Java-Native ML" "$Root\01-java-native-ml" "mvn spring-boot:run"
}

function Stop-Example01 {
    Write-Head "01 - Stop Java-Native ML"
    Stop-ServicePort 8082 "Java-Native ML"
    Stop-PythonService
}

# =============================================================================
# Example 02 - ONNX In-JVM  :8081
#   Requires fraud_model.onnx - run python-train-export first if missing
# =============================================================================

function Start-Example02 {
    Write-Head "02 - ONNX In-JVM  port 8081"
    Start-PythonService
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
    Stop-PythonService
}

# =============================================================================
# Example 03 - Model as a Service  :8000 (Python) + :8080 (Java)
# =============================================================================

function Start-Example03 {
    Write-Head "03 - Model as a Service  port 8000 (Python FastAPI) + 8080 (Java)"
    Start-PythonService

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
    Stop-ServicePort 8080 "Java Spring Client"
    Stop-PythonService
}

# =============================================================================
# Example 04 - Spring AI (LLM Fraud Explainer)  :8006
# =============================================================================

function Start-Example04 {
    Write-Head "04 - Spring AI (LLM Fraud Explainer)  port 8006"
    Start-PythonService
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
    Stop-PythonService
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

    # Ensure com.docker.backend is running — it creates the named pipe bridge
    if (-not (Get-Process "com.docker.backend" -ErrorAction SilentlyContinue)) {
        $backendExe = "C:\Program Files\Docker\Docker\resources\com.docker.backend.exe"
        if (Test-Path $backendExe) {
            Write-Info "Docker backend not running - starting it ..."
            Start-Process $backendExe -WindowStyle Hidden
        } else {
            # Fall back to launching the full Docker Desktop UI
            $dockerDesktopExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
            if (Test-Path $dockerDesktopExe) {
                Write-Info "Starting Docker Desktop ..."
                Start-Process $dockerDesktopExe
            } else {
                Write-Fail "Docker Desktop not found - install it and try again"
                return
            }
        }
    } else {
        Write-Info "Docker backend is running - waiting for engine pipe ..."
    }

    # Wait up to 90 s for the engine pipe to appear, then lock in the right context
    $deadline = (Get-Date).AddSeconds(90)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        $pipes = Get-ChildItem \\.\pipe\ -ErrorAction SilentlyContinue |
                 Select-Object -ExpandProperty Name
        if ($pipes -contains 'dockerDesktopLinuxEngine') {
            docker context use desktop-linux | Out-Null
            Write-Ok "Docker engine ready  (context -> desktop-linux)"
            $ready = $true; break
        } elseif ($pipes -contains 'docker_engine') {
            docker context use default | Out-Null
            Write-Ok "Docker engine ready  (context -> default)"
            $ready = $true; break
        }
        $remaining = [int](($deadline - (Get-Date)).TotalSeconds)
        Write-Info "Waiting for Docker engine pipe ... ($remaining s remaining)"
        Start-Sleep 5
    }

    if (-not $ready) {
        Write-Fail "Docker engine did not start within 90 s"
        Write-Info "Try:  docker desktop restart"
        Write-Info "Then re-run:  .\demo.ps1 start 05"
        return
    }

    # Pipe is up but the engine may still be initialising — wait until docker info succeeds
    $apiDeadline = (Get-Date).AddSeconds(60)
    $apiReady = $false
    while ((Get-Date) -lt $apiDeadline) {
        $null = docker info 2>&1
        if ($LASTEXITCODE -eq 0) { $apiReady = $true; break }
        $remaining = [int](($apiDeadline - (Get-Date)).TotalSeconds)
        Write-Info "Waiting for Docker API to be ready ... ($remaining s remaining)"
        Start-Sleep 3
    }
    if (-not $apiReady) {
        Write-Fail "Docker API did not become ready within 60 s after pipe appeared"
        Write-Info "Try:  docker desktop restart"
        Write-Info "Then re-run:  .\demo.ps1 start 05"
        return
    }
    Write-Ok "Docker API ready"

    # Check if containers already exist (stopped) — if so, just start them (much faster)
    Push-Location "$Root\05-full-pipeline"
    $existingContainers = & docker compose ps --all --quiet 2>$null
    Pop-Location

    if ($existingContainers) {
        Write-Info "Existing containers found - restarting them (skipping rebuild) ..."
        Invoke-DockerCompose "start"
    } else {
        Write-Info "No existing containers - building and creating stack ..."
        Invoke-DockerCompose "up", "--build", "-d"
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "docker compose failed - check the output above"
        return
    }
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

    Invoke-DockerCompose "stop"
    Write-Ok "Stack stopped (containers preserved for fast restart)"
    Write-Info "  To fully remove containers:  docker compose down  (from 05-full-pipeline folder)"
}

# =============================================================================
# Status - check all ports
# =============================================================================

function Show-ExampleStatus([string]$Ex) {
    switch ($Ex) {
        "01" {
            Write-Head "01 - Java-Native ML"
            Show-PortStatus "Python ML service" 8000
            Show-PortStatus "Java-Native ML (Weka)" 8082
        }
        "02" {
            Write-Head "02 - ONNX In-JVM"
            Show-PortStatus "Python ML service" 8000
            Show-PortStatus "ONNX In-JVM" 8081
        }
        "03" {
            Write-Head "03 - Model as a Service"
            Show-PortStatus "Python ML service" 8000
            Show-PortStatus "Java Spring Client" 8080
        }
        "04" {
            Write-Head "04 - Spring AI"
            Show-PortStatus "Python ML service" 8000
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
