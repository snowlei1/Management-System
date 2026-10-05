<# Read-only checks for the named local project database and controlled resource directory.
   Reports issues only; never repairs/deletes files or historical rows. #>
param([string]$StorageDirectory)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(-not $StorageDirectory){$StorageDirectory=if($env:RESOURCE_STORAGE_DIR){$env:RESOURCE_STORAGE_DIR}else{Join-Path $project 'backend/uploads/resources'}}
$root=[IO.Path]::GetFullPath($StorageDirectory)
function SQL([string]$query){
    $local=[IO.File]::ReadAllText((Join-Path $project 'backend/config/application-local.yml'))
    $dbUser=[regex]::Match($local,'(?m)^\s*username:\s*["'']?([^\s"'']+)').Groups[1].Value
    $dbSecret=[regex]::Match($local,'(?m)^\s*password:\s*["'']?([^\s"'']+)').Groups[1].Value
    $previous=$env:MYSQL_PWD;$env:MYSQL_PWD=$dbSecret
    try{$rows=@(& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --user=$dbUser --batch --skip-column-names --default-character-set=utf8mb4 '--database=management-system' --execute=$query);if($LASTEXITCODE -ne 0){throw 'MySQL check failed'};return $rows}
    finally{if($null -eq $previous){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previous};$local=$null;$dbSecret=$null}
}
$checks=[ordered]@{
    approvedWithoutPublication="SELECT COUNT(*) FROM teaching_resource WHERE status='APPROVED' AND published_at IS NULL"
    unexpectedPublication="SELECT COUNT(*) FROM teaching_resource WHERE status<>'APPROVED' AND published_at IS NOT NULL"
    duplicateAuditRounds='SELECT COUNT(*) FROM (SELECT resource_id,submission_no FROM audit_record GROUP BY resource_id,submission_no HAVING COUNT(*)>1) x'
    orphanResourceBase='SELECT COUNT(*) FROM teaching_resource r LEFT JOIN app_user u ON u.id=r.created_by LEFT JOIN course c ON c.id=r.course_id LEFT JOIN resource_category k ON k.id=r.category_id WHERE u.id IS NULL OR c.id IS NULL OR k.id IS NULL'
    orphanElements='SELECT COUNT(*) FROM resource_element_relation x LEFT JOIN teaching_resource r ON r.id=x.resource_id LEFT JOIN ideological_element e ON e.id=x.element_id WHERE r.id IS NULL OR e.id IS NULL'
    orphanAudits='SELECT COUNT(*) FROM audit_record a LEFT JOIN teaching_resource r ON r.id=a.resource_id LEFT JOIN app_user u ON u.id=a.reviewer_id WHERE r.id IS NULL OR u.id IS NULL'
    duplicateFavorites='SELECT COUNT(*) FROM (SELECT user_id,resource_id FROM favorite GROUP BY user_id,resource_id HAVING COUNT(*)>1) x'
    orphanFavorites='SELECT COUNT(*) FROM favorite f LEFT JOIN teaching_resource r ON r.id=f.resource_id LEFT JOIN app_user u ON u.id=f.user_id WHERE r.id IS NULL OR u.id IS NULL'
    orphanBrowse='SELECT COUNT(*) FROM browse_record b LEFT JOIN teaching_resource r ON r.id=b.resource_id LEFT JOIN app_user u ON u.id=b.user_id WHERE r.id IS NULL OR u.id IS NULL'
    orphanDownload='SELECT COUNT(*) FROM download_record d LEFT JOIN teaching_resource r ON r.id=d.resource_id LEFT JOIN app_user u ON u.id=d.user_id WHERE r.id IS NULL OR u.id IS NULL'
    invalidState='SELECT COUNT(*) FROM teaching_resource WHERE status NOT IN (''DRAFT'',''PENDING'',''REJECTED'',''APPROVED'')'
    nonBcryptPasswords="SELECT COUNT(*) FROM app_user WHERE password_hash NOT REGEXP '^[$]2[aby][$][0-9]{2}[$].{53}$'"
}
$results=[ordered]@{};foreach($pair in $checks.GetEnumerator()){$results[$pair.Key]=[long](SQL $pair.Value)}
$references=@{};$missing=@();$unsafe=@();$sizeMismatch=@();$resourceRows=0
foreach($line in (SQL 'SELECT id,file_storage_key,file_size_bytes FROM teaching_resource ORDER BY id')){
    $parts=$line.Split("`t");$resourceRows++;$key=$parts[1];$references[$key]=$true
    if($key -notmatch '^[a-f0-9-]{36}\.(pdf|doc|docx|ppt|pptx|xls|xlsx|png|jpg|jpeg)$'){$unsafe+=$parts[0];continue}
    $path=[IO.Path]::GetFullPath((Join-Path $root $key))
    if(-not $path.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){$unsafe+=$parts[0];continue}
    if(-not [IO.File]::Exists($path)){$missing+=$parts[0]}
    elseif(([IO.FileInfo]$path).Length -ne [long]$parts[2]){$sizeMismatch+=$parts[0]}
}
$files=@();if([IO.Directory]::Exists($root)){$files=@([IO.Directory]::EnumerateFiles($root,'*',[IO.SearchOption]::AllDirectories))}
$unreferenced=@();foreach($path in $files){$relative=[IO.Path]::GetRelativePath($root,$path);if(-not $references.ContainsKey($relative)){$unreferenced+=$relative}}
[pscustomobject]@{
    DatabaseChecks=$results;ResourceRows=$resourceRows;UniqueFileKeys=$references.Count;DiskFiles=$files.Count
    MissingResourceIds=$missing;UnsafeResourceIds=$unsafe;SizeMismatchResourceIds=$sizeMismatch;UnreferencedFiles=$unreferenced
    Scope='All resource rows including soft-deleted; read-only local development snapshot; no automatic cleanup'
}|ConvertTo-Json -Depth 8
if(@($results.Values|Where-Object {$_ -ne 0}).Count -or $missing.Count -or $unsafe.Count -or $sizeMismatch.Count -or $unreferenced.Count){throw 'Consistency differences found; inspect report before any repair'}
