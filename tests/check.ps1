$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    New-Item -ItemType Directory -Force build/checks | Out-Null
    & "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d build/checks app/src/main/java/com/privatecalc/vault/Calculator.java app/src/main/java/com/privatecalc/vault/EncryptedMedia.java tests/CalculatorTest.java tests/EncryptedMediaTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    foreach ($test in @('CalculatorTest', 'EncryptedMediaTest')) {
        & "$env:JAVA_HOME\bin\java.exe" -cp build/checks "com.privatecalc.vault.$test"
        if ($LASTEXITCODE -ne 0) { throw "$test failed" }
    }
} finally { Pop-Location }
