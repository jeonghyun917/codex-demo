param(
    [Parameter(Mandatory=$true)][string]$Report,
    [Parameter(Mandatory=$true)][string]$IndependentTsv
)
$ErrorActionPreference='Stop'
$data=Get-Content -LiteralPath $Report -Raw | ConvertFrom-Json
$lines=@(Get-Content -LiteralPath $IndependentTsv)
$header=-1
for($i=0;$i -lt $lines.Count;$i++) {
    if($lines[$i].StartsWith("index_code`tmodel_version`tusable_20d`t")) {$header=$i;break}
}
if($header -lt 0){throw 'Independent metrics section not found'}
$rows=@($lines[$header..($lines.Count-1)] | ConvertFrom-Csv -Delimiter "`t")
$independent=@{}
foreach($row in $rows){
    $key=$row.index_code+':'+$row.model_version
    if($independent.ContainsKey($key)){throw 'Duplicate independent result key'}
    $independent[$key]=$row
}
if(@($data.researchReports).Count -ne 8 -or $independent.Count -ne 8){
    throw 'Expected exactly eight completed model versions in one research namespace'
}
$checks=@()
foreach($reportRow in $data.researchReports){
    $key=$reportRow.indexCode+':'+$reportRow.modelVersion
    if(-not $independent.ContainsKey($key)){throw "Missing independent result: $key"}
    if($reportRow.classification -ne 'RECONSTRUCTED_RESEARCH_NOT_PIT' -or $reportRow.promotionAllowed){
        throw "Research classification changed: $key"
    }
    $sql=$independent[$key]
    foreach($value in @($reportRow.metrics.brierScore,$reportRow.metrics.maePct,
            $reportRow.metrics.directionalAccuracyPct,$sql.independently_recomputed_brier,
            $sql.independently_recomputed_mae_pct,$sql.direction_accuracy_pct)) {
        if($null -eq $value -or [double]::IsNaN([double]$value) -or [double]::IsInfinity([double]$value)){
            throw "Non-finite metric: $key"
        }
    }
    $brierDelta=[Math]::Abs([double]$reportRow.metrics.brierScore-[double]$sql.independently_recomputed_brier)
    $maeDelta=[Math]::Abs([double]$reportRow.metrics.maePct-[double]$sql.independently_recomputed_mae_pct)
    $directionDelta=[Math]::Abs([double]$reportRow.metrics.directionalAccuracyPct-[double]$sql.direction_accuracy_pct)
    $countMatches=([int]$reportRow.usableRows -eq [int]$sql.usable_20d)
    if(-not $countMatches -or $brierDelta -gt 1e-10 -or $maeDelta -gt 1e-5 -or $directionDelta -gt 0.0001){
        throw "Independent metric discrepancy: $key"
    }
    if([int]$reportRow.targetCount -lt [int]$reportRow.usableRows){throw 'Invalid population denominator'}
    $coverage=if($reportRow.targetCount -eq 0){0}else{100.0*$reportRow.usableRows/$reportRow.targetCount}
    if([Math]::Abs($coverage-$reportRow.coveragePct) -gt 1e-9){throw 'Coverage formula mismatch'}
    $checks += [pscustomobject]@{
        index=$reportRow.indexCode;model=$reportRow.modelVersion;usableRows=$reportRow.usableRows
        targetCount=$reportRow.targetCount;brierAbsoluteDifference=$brierDelta
        maePercentagePointDifference=$maeDelta;directionPercentagePointDifference=$directionDelta
        countMatches=$countMatches;promotionAllowed=$false
    }
}
[pscustomobject]@{
    status='MATCHED';classification='DESCRIPTIVE_RESEARCH_ONLY';asOf=$data.asOf
    reportPath=$Report;independentSqlPath='scripts/quant_research_validation_checks.sql'
    independentOutputPath=$IndependentTsv;checks=$checks
} | ConvertTo-Json -Depth 5
