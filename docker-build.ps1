# PowerShell script to build and run Docker image

param(
    [Parameter(Mandatory=$false)]
    [string]$ImageName = "casepack-optimizer",

    [Parameter(Mandatory=$false)]
    [string]$Tag = "latest",

    [Parameter(Mandatory=$false)]
    [switch]$NoBuild,

    [Parameter(Mandatory=$false)]
    [switch]$Run,

    [Parameter(Mandatory=$false)]
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"

# Colors for output
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

Write-ColorOutput Green "=== Casepack Optimizer Docker Build Script ==="

# Build the JAR file first (unless -NoBuild is specified)
if (-not $NoBuild) {
    Write-ColorOutput Yellow "`nBuilding application JAR..."
    try {
        .\gradlew.bat clean bootJar
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed"
        }
        Write-ColorOutput Green "✓ JAR build successful"
    } catch {
        Write-ColorOutput Red "✗ JAR build failed: $_"
        exit 1
    }
}

# Build Docker image
Write-ColorOutput Yellow "`nBuilding Docker image..."
$imageFull = "${ImageName}:${Tag}"

try {
    docker build -t $imageFull .
    if ($LASTEXITCODE -ne 0) {
        throw "Docker build failed"
    }
    Write-ColorOutput Green "✓ Docker image built successfully: $imageFull"
} catch {
    Write-ColorOutput Red "✗ Docker build failed: $_"
    exit 1
}

# List the image
Write-ColorOutput Cyan "`nDocker image details:"
docker images $ImageName

# Run the container if -Run flag is provided
if ($Run) {
    Write-ColorOutput Yellow "`nRunning Docker container..."

    # Stop existing container if running
    $existingContainer = docker ps -aq -f name=casepack-optimizer-app
    if ($existingContainer) {
        Write-ColorOutput Yellow "Stopping existing container..."
        docker stop casepack-optimizer-app | Out-Null
        docker rm casepack-optimizer-app | Out-Null
    }

    # Run new container
    try {
        docker run -d `
            --name casepack-optimizer-app `
            -p ${Port}:8080 `
            $imageFull

        if ($LASTEXITCODE -ne 0) {
            throw "Docker run failed"
        }

        Write-ColorOutput Green "✓ Container started successfully"
        Write-ColorOutput Cyan "`nContainer is running at: http://localhost:$Port"
        Write-ColorOutput Cyan "API endpoint: http://localhost:$Port/api/v1/casepack/optimize"

        # Show container logs
        Write-ColorOutput Yellow "`nContainer logs (last 20 lines):"
        Start-Sleep -Seconds 3
        docker logs --tail 20 casepack-optimizer-app

    } catch {
        Write-ColorOutput Red "✗ Failed to run container: $_"
        exit 1
    }
}

Write-ColorOutput Green "`n=== Build completed successfully ===`n"

# Display helpful commands
Write-ColorOutput Cyan "Useful commands:"
Write-Output "  Run container:    docker run -p 8080:8080 $imageFull"
Write-Output "  View logs:        docker logs casepack-optimizer-app"
Write-Output "  Stop container:   docker stop casepack-optimizer-app"
Write-Output "  Remove container: docker rm casepack-optimizer-app"
Write-Output "  Shell access:     docker exec -it casepack-optimizer-app /bin/sh"