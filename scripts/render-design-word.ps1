param(
    [Parameter(Mandatory=$true)][string]$PopplerBin,
    [Parameter(Mandatory=$true)][string]$PythonExe,
    [int]$Dpi = 120
)
$ErrorActionPreference = 'Stop'
$designRepoRoot = Split-Path -Parent $PSScriptRoot
$designInput = Join-Path $designRepoRoot '文本文档/04_迭代2_系统设计说明书.docx'
$designRenderDirectory = Join-Path $designRepoRoot 'target/design-word/render'
New-Item -ItemType Directory -Force -Path $designRenderDirectory | Out-Null
$designRenderAbsolute = [IO.Path]::GetFullPath($designRenderDirectory)
foreach ($designOldPage in (Get-ChildItem -LiteralPath $designRenderDirectory -File -Filter 'page-*.png')) {
    if ($designOldPage.Name -match '^page-\d+\.png$' -and $designOldPage.DirectoryName -eq $designRenderAbsolute) {
        Remove-Item -LiteralPath $designOldPage.FullName
    }
}
$designPdf = Join-Path $designRenderDirectory 'iteration2-design.pdf'
$designWord = $null
$designDocument = $null
try {
    $designWord = New-Object -ComObject Word.Application
    $designWord.Visible = $false
    $designWord.DisplayAlerts = 0
    $designWord.AutomationSecurity = 3
    # Open only the generated document, read-only, without adding recent files.
    $designDocument = $designWord.Documents.Open($designInput, $false, $true, $false)
    $designDocument.Repaginate()
    $null = $designDocument.Fields.Update()
    foreach ($designSection in $designDocument.Sections) {
        foreach ($designFooter in $designSection.Footers) { $null = $designFooter.Range.Fields.Update() }
    }
    $designPageCount = $designDocument.ComputeStatistics(2)
    $designDocument.ExportAsFixedFormat($designPdf, 17)
    Write-Output ('Word PDF pages: ' + $designPageCount)
} finally {
    if ($null -ne $designDocument) {
        try { $designDocument.Close(0) } catch { Write-Warning 'Document close did not complete normally' }
        $null = [Runtime.InteropServices.Marshal]::FinalReleaseComObject($designDocument)
    }
    if ($null -ne $designWord) {
        try { $designWord.Quit(0) } catch { Write-Warning 'Word quit did not complete normally' }
        $null = [Runtime.InteropServices.Marshal]::FinalReleaseComObject($designWord)
    }
}
& (Join-Path $PopplerBin 'pdftoppm.exe') -r $Dpi -png $designPdf (Join-Path $designRenderDirectory 'page')
if ($LASTEXITCODE -ne 0) { throw 'PDF rasterization failed' }
& $PythonExe -c 'from pathlib import Path; from pypdf import PdfReader; import sys; p=Path(sys.argv[1]); p.with_name("document.txt").write_text("\n\f\n".join(page.extract_text() for page in PdfReader(p).pages),encoding="utf-8")' $designPdf
if ($LASTEXITCODE -ne 0) { throw 'PDF text extraction failed' }
Write-Output ('Render output: ' + $designRenderDirectory)
