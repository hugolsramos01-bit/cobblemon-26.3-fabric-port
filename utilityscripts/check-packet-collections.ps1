param(
    [string] $JavaHome = $env:JAVA_HOME,
    [string] $GradleCache = (Join-Path $env:USERPROFILE '.gradle\caches'),
    [string] $OutputDirectory = (Join-Path $env:TEMP 'cobblemon-packet-check'),
    [ValidateSet('PacketCollections', 'RegistryProperties', 'FoodConsumables', 'ItemTooltips', 'RecipeIngredients', 'AnimationPixels', 'RenderFactories')]
    [string] $Check = 'PacketCollections'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { throw 'Pass -JavaHome with the local JDK 25 directory.' }
$javaExe = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw 'Pass -JavaHome with the local JDK 25 directory.'
}
$minecraftVersion = ((Get-Content -LiteralPath (Join-Path $repoRoot 'gradle.properties') |
    Where-Object { $_ -match '^mc_version\s*=' } | Select-Object -First 1) -split '=', 2)[1].Trim()
$kotlinVersion = [regex]::Match(
    [IO.File]::ReadAllText((Join-Path $repoRoot 'gradle\libs.versions.toml')),
    '(?m)^kotlin\s*=\s*"([^"]+)"'
).Groups[1].Value
$moduleCache = Join-Path $GradleCache 'modules-2\files-2.1'

function Find-CachedJar([string] $Group, [string] $Artifact, [string] $Version) {
    $directory = Join-Path $moduleCache "$Group\$Artifact\$Version"
    $file = Get-ChildItem -LiteralPath $directory -Recurse -Filter "$Artifact-$Version.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $file) {
        throw "Missing cached dependency $Group`:$Artifact`:$Version. Resolve the Gradle dependencies first."
    }
    return $file.FullName
}

$compilerJar = Find-CachedJar 'org.jetbrains.kotlin' 'kotlin-compiler-embeddable' $kotlinVersion
$compilerPom = Get-ChildItem -LiteralPath (Join-Path $moduleCache "org.jetbrains.kotlin\kotlin-compiler-embeddable\$kotlinVersion") -Recurse -Filter '*.pom' |
    Select-Object -First 1
[xml] $pom = Get-Content -LiteralPath $compilerPom.FullName -Raw
$compilerJars = @($compilerJar)
foreach ($dependency in $pom.project.dependencies.dependency) {
    $compilerJars += Find-CachedJar $dependency.groupId $dependency.artifactId $dependency.version
}
$annotations = Get-ChildItem -LiteralPath (Join-Path $moduleCache 'org.jetbrains\annotations') -Recurse -Filter '*.jar' |
    Select-Object -Last 1
if (-not $annotations) { throw 'Missing cached JetBrains annotations dependency.' }
$compilerJars += $annotations.FullName

$minecraftMetadata = Join-Path $GradleCache "fabric-loom\$minecraftVersion\mojang_minecraft_info.json"
$info = Get-Content -LiteralPath $minecraftMetadata -Raw | ConvertFrom-Json
$minecraftJar = Join-Path $GradleCache "fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\$minecraftVersion\minecraft-merged-deobf-$minecraftVersion.jar"
if (-not (Test-Path -LiteralPath $minecraftJar)) { throw "Missing Minecraft JAR: $minecraftJar" }
$runtimeJars = @($minecraftJar, (Find-CachedJar 'org.jetbrains.kotlin' 'kotlin-stdlib' $kotlinVersion), $annotations.FullName)
foreach ($library in $info.libraries) {
    $coordinate = $library.name -split ':'
    if ($coordinate.Count -ne 3) { continue }
    $directory = Join-Path $moduleCache ($coordinate -join '\')
    $file = Get-ChildItem -LiteralPath $directory -Recurse -Filter "$($coordinate[1])-$($coordinate[2]).jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($file) { $runtimeJars += $file.FullName }
}
$runtimeClasspath = $runtimeJars -join ';'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$sourceFiles = @(if ($Check -eq 'AnimationPixels') {
    @((Join-Path $repoRoot 'common\src\main\kotlin\com\cobblemon\mod\common\client\render\AnimatedTexturePixels.kt'))
} elseif ($Check -in @('RecipeIngredients', 'RenderFactories')) {
    @()
} elseif ($Check -eq 'ItemTooltips') {
    @(
        (Join-Path $repoRoot 'common\src\main\kotlin\com\cobblemon\mod\common\item\HealingMachineItem.kt'),
        (Join-Path $repoRoot 'common\src\main\kotlin\com\cobblemon\mod\common\api\cooking\effects\CleanseAllEffect.kt'),
        (Join-Path $repoRoot 'common\src\main\kotlin\com\cobblemon\mod\common\api\cooking\effects\CleanseNegativeEffect.kt')
    )
} elseif ($Check -eq 'FoodConsumables') {
    @((Join-Path $repoRoot 'common\src\main\kotlin\com\cobblemon\mod\common\util\FoodConsumables.kt'))
} elseif ($Check -eq 'RegistryProperties') {
    @((Join-Path $repoRoot 'common\src\main\kotlin\com\cobblemon\mod\common\util\RegistryProperties.kt'))
} else {
    @(
        (Join-Path $repoRoot 'common\src\main\kotlin\com\cobblemon\mod\common\util\PacketCollectionUtils.kt'),
        (Join-Path $repoRoot 'common\src\main\kotlin\com\cobblemon\mod\common\util\CobblemonInteractionResults.kt')
    )
})
$sourceFiles += Join-Path $PSScriptRoot "checks\${Check}Check.kt"
& $javaExe -cp ($compilerJars -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler `
    -no-stdlib -no-reflect -jvm-target 25 -classpath $runtimeClasspath -d $OutputDirectory `
    @sourceFiles
if ($LASTEXITCODE -ne 0) { throw "$Check check compilation failed: exit $LASTEXITCODE" }
& $javaExe -cp "$OutputDirectory;$runtimeClasspath" "${Check}CheckKt"
if ($LASTEXITCODE -ne 0) { throw "$Check checks failed: exit $LASTEXITCODE" }
