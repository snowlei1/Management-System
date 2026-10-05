<# Local-only stage 3 HTTP verification. Requires running servers and dev-seed.sql.
   Creates random test fixtures; finally soft-deletes resources and deactivates base data.
   -KeepFixtures retains ACTIVE base data for subsequent browser checks, not resource drafts.
#>
param([string]$BaseUrl='http://127.0.0.1:5173', [switch]$KeepFixtures, [switch]$CreateBrowserFixtures)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.IO.Compression
$script:passed=0; $script:requests=0; $script:resources=@(); $script:rows=@(); $script:contexts=@()
$tag=[guid]::NewGuid().ToString('N').Substring(0,8)
function Context {
    $handler=[System.Net.Http.HttpClientHandler]::new(); $handler.CookieContainer=[Net.CookieContainer]::new()
    $client=[Net.Http.HttpClient]::new($handler); $client.Timeout=[TimeSpan]::FromSeconds(90)
    $ctx=@{ Client=$client; Csrf=$null; Header=$null }; $script:contexts+=,$ctx; return $ctx
}
function Request($ctx,[string]$method,[string]$path,$body=$null,$file=$null,[switch]$Multipart,[switch]$NoCsrf) {
    $script:requests++
    $req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($method),"$BaseUrl/api$path")
    $req.Headers.Accept.ParseAdd('application/json')
    if ($method -notin @('GET','HEAD') -and -not $NoCsrf -and $ctx.Csrf) { [void]$req.Headers.TryAddWithoutValidation($ctx.Header,$ctx.Csrf) }
    if ($Multipart) {
        $content=[Net.Http.MultipartFormDataContent]::new()
        $content.Add([Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress -Depth 10),[Text.Encoding]::UTF8,'application/json'),'metadata')
        if ($null -ne $file) {
            $part=[Net.Http.ByteArrayContent]::new([byte[]]$file.Bytes); $part.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new($file.Mime)
            $content.Add($part,'file',$file.Name)
        }
        $req.Content=$content
    } elseif ($null -ne $body) { $req.Content=[Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress -Depth 10),[Text.Encoding]::UTF8,'application/json') }
    $response=$null
    try {
        $response=$ctx.Client.SendAsync($req).GetAwaiter().GetResult()
        $text=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $json=$null; if ($text) { try { $json=$text|ConvertFrom-Json } catch { } }
        return @{Status=[int]$response.StatusCode; Json=$json}
    } finally { if ($response) { $response.Dispose() }; $req.Dispose() }
}
function Assert([string]$name,[bool]$ok) { if (-not $ok) { throw "FAIL: $name" }; $script:passed++; Write-Host "PASS: $name" }
function Status([string]$name,$r,[int]$expected) {
    Assert "$name HTTP $($r.Status)" ($r.Status -eq $expected)
    Assert "$name unified response" ([bool]$r.Json.code -and [bool]$r.Json.message -and (($expected -ge 300) -or $r.Json.code -eq 'OK'))
}
function Csrf($ctx) { $r=Request $ctx GET '/auth/csrf'; if ($r.Status -ne 200) {throw 'CSRF unavailable'}; $ctx.Csrf=$r.Json.data.token; $ctx.Header=$r.Json.data.headerName }
function Login([string]$username,[string]$password) { $ctx=Context; Csrf $ctx; Status "$username login" (Request $ctx POST '/auth/login' @{username=$username;password=$password}) 200; Csrf $ctx; return $ctx }
function BaseRow([string]$type,[string]$suffix) {
    $p=@{name="S3-$tag-$suffix";description='第三阶段本地验证'}; if ($type -eq 'courses') {$p.courseCode="S3-$tag-$suffix"}
    $r=Request $admin POST "/$type" $p; Status "$type fixture" $r 201; $script:rows+=@{Type=$type;Id=$r.Json.data.id}; return $r.Json.data.id
}
function PDF {
    $parts=@('%PDF-1.4',"1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj", "2 0 obj`n<< /Type /Pages /Kids [3 0 R] /Count 1 >>`nendobj", "3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>`nendobj", "4 0 obj`n<< /Length 49 >>`nstream`nBT /F1 16 Tf 30 100 Td (Stage 3 test resource) Tj ET`nendstream`nendobj", "5 0 obj`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>`nendobj")
    $text=$parts[0]+"`n"; $offsets=@(); foreach ($p in $parts[1..5]) {$offsets+=[Text.Encoding]::ASCII.GetByteCount($text);$text+=$p+"`n"}
    $xref=[Text.Encoding]::ASCII.GetByteCount($text); $text+="xref`n0 6`n0000000000 65535 f `n"; foreach ($o in $offsets) {$text+=('{0:D10} 00000 n ' -f $o)+"`n"}
    $text+="trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n"; return ,[Text.Encoding]::ASCII.GetBytes($text)
}
function DOCX {
    $mem=[IO.MemoryStream]::new(); $zip=[IO.Compression.ZipArchive]::new($mem,[IO.Compression.ZipArchiveMode]::Create,$true)
    $parts=@{'[Content_Types].xml'='<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'; '_rels/.rels'='<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'; 'word/document.xml'='<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>第三阶段教学资源测试文档</w:t></w:r></w:p><w:sectPr/></w:body></w:document>'}
    foreach ($name in $parts.Keys) { $entry=$zip.CreateEntry($name);$s=$entry.Open();$b=[Text.Encoding]::UTF8.GetBytes($parts[$name]);$s.Write($b,0,$b.Length);$s.Dispose() }
    $zip.Dispose();$result=$mem.ToArray();$mem.Dispose();return ,$result
}
function Create($p,$f,[string]$name='create') { $r=Request $teacher POST '/teacher/resources' $p $f -Multipart; Status $name $r 201; $script:resources+=,$r.Json.data.id; return $r.Json.data }
function CopyPayload { return @{title="阶段三-$tag";description='草稿简介';courseId=$course;categoryId=$category;elementIds=@($e1,$e2)} }
if ($CreateBrowserFixtures) {
    $fixtureDir=Join-Path (Split-Path $PSScriptRoot -Parent) "tmp/stage3-browser-$tag"
    [void](New-Item -ItemType Directory -Path $fixtureDir -Force)
    [IO.File]::WriteAllBytes((Join-Path $fixtureDir '课程 思政 (案例)#1.pdf'),(PDF))
    [IO.File]::WriteAllBytes((Join-Path $fixtureDir '教学 案例.docx'),(DOCX))
    Write-Output "BROWSER_FIXTURE_DIRECTORY=$fixtureDir"
    return
}
try {
    $admin=Login 'dev_admin' 'AdminDev#2026'; $teacher=Login 'dev_teacher' 'TeacherDev#2026'; $student=Login 'dev_student' 'StudentDev#2026'
    $existing=Request $admin GET '/users?keyword=dev_teacher_b&role=TEACHER'
    if (-not @($existing.Json.data.items|Where-Object username -eq 'dev_teacher_b').Count) { Status 'create teacher B' (Request $admin POST '/users' @{username='dev_teacher_b';displayName='草稿越权测试教师';role='TEACHER';password='TeacherBDev#2026'}) 201 }
    $teacherB=Login 'dev_teacher_b' 'TeacherBDev#2026';$anonymous=Context;Csrf $anonymous
    $course=BaseRow courses '课程';$category=BaseRow resource-categories '分类';$e1=BaseRow ideological-elements '元素1';$e2=BaseRow ideological-elements '元素2'
    $pdf=@{Name='课程 思政 (案例)#1.pdf';Mime='application/pdf';Bytes=(PDF)}
    $docx=@{Name='教学 案例.docx';Mime='application/vnd.openxmlformats-officedocument.wordprocessingml.document';Bytes=(DOCX)}
    $png=@{Name='教学 图片.png';Mime='image/png';Bytes=[Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jH1kAAAAASUVORK5CYII=')}
    $policy=Request $teacher GET '/teacher/resources/upload-policy';Status 'upload policy' $policy 200;Assert '20 MiB policy' ($policy.Json.data.maxSizeBytes -eq 20*1024*1024)
    $payload=CopyPayload;$payload.elementIds=@($e2,$e1,$e1)
    $draft=Create $payload $pdf 'teacher A PDF creation';$id=$draft.id
    Assert 'deduplicated multi-element relation' (@($draft.elements).Count -eq 2)
    Assert 'session creator and fixed DRAFT' ($draft.createdBy -eq (Request $teacher GET '/auth/me').Json.data.id -and $draft.status -eq 'DRAFT')
    Assert 'no storage path exposed' (-not ($draft.PSObject.Properties.Name -match 'storage|path'))
    Status 'owner detail' (Request $teacher GET "/teacher/resources/$id") 200
    foreach ($actor in @(@{Name='teacher B';Ctx=$teacherB},@{Name='student';Ctx=$student},@{Name='admin';Ctx=$admin})) {
        $expect=if($actor.Name -eq 'teacher B'){404}else{403}
        Status "$($actor.Name) detail denied" (Request $actor.Ctx GET "/teacher/resources/$id") $expect
        Status "$($actor.Name) edit denied" (Request $actor.Ctx PUT "/teacher/resources/$id" (CopyPayload) $null -Multipart) $expect
        Status "$($actor.Name) delete denied" (Request $actor.Ctx DELETE "/teacher/resources/$id") $expect
        if ($actor.Name -ne 'teacher B') {
            Status "$($actor.Name) create denied" (Request $actor.Ctx POST '/teacher/resources' (CopyPayload) $pdf -Multipart) 403
            Status "$($actor.Name) list denied" (Request $actor.Ctx GET '/teacher/resources') 403
        }
    }
    Assert 'teacher B list isolation' ($id -notin @((Request $teacherB GET '/teacher/resources').Json.data.items|ForEach-Object id))
    Status 'anonymous list' (Request $anonymous GET '/teacher/resources') 401
    Status 'anonymous write with CSRF' (Request $anonymous POST '/teacher/resources' (CopyPayload) $pdf -Multipart) 401
    Status 'missing CSRF create' (Request $teacher POST '/teacher/resources' (CopyPayload) $pdf -Multipart -NoCsrf) 403
    Status 'missing CSRF edit' (Request $teacher PUT "/teacher/resources/$id" (CopyPayload) $null -Multipart -NoCsrf) 403
    Status 'missing CSRF delete' (Request $teacher DELETE "/teacher/resources/$id" -NoCsrf) 403
    foreach ($field in @('courseId','categoryId','elementIds')) {
        $bad=CopyPayload;$bad[$field]=if($field -eq 'elementIds'){@(9223372036854775807)}else{9223372036854775807}
        Status "nonexistent $field" (Request $teacher POST '/teacher/resources' $bad $pdf -Multipart) 400
    }
    foreach ($row in @(@{Type='courses';Id=$course},@{Type='resource-categories';Id=$category},@{Type='ideological-elements';Id=$e1})) {
        Status 'deactivate reference' (Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='INACTIVE'}) 200
        Status "inactive $($row.Type) create" (Request $teacher POST '/teacher/resources' (CopyPayload) $pdf -Multipart) 400
        Status "inactive $($row.Type) edit" (Request $teacher PUT "/teacher/resources/$id" (CopyPayload) $null -Multipart) 400
        $history=Request $teacher GET "/teacher/resources/$id";Status 'historical reference retained' $history 200;Assert 'historical elements intact' (@($history.Json.data.elements).Count -eq 2)
        Status 'reactivate reference' (Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='ACTIVE'}) 200
    }
    foreach ($field in @('createdBy','creator_id','status')) {
        $bad=CopyPayload;$bad[$field]=if($field -eq 'status'){'APPROVED'}else{1}
        Status "forged $field" (Request $teacher POST '/teacher/resources' $bad $pdf -Multipart) 400
    }
    $bad=CopyPayload;$bad.courseId=[double]$course+0.5;Status 'fractional course ID' (Request $teacher POST '/teacher/resources' $bad $pdf -Multipart) 400
    Status 'JSON instead of multipart' (Request $teacher POST '/teacher/resources' (CopyPayload)) 415
    $bad=CopyPayload;$bad.title=' ';Status 'blank title' (Request $teacher POST '/teacher/resources' $bad $pdf -Multipart) 400
    $bad=CopyPayload;$bad.elementIds=@(-1);Status 'invalid element ID' (Request $teacher POST '/teacher/resources' $bad $pdf -Multipart) 400
    $zero=CopyPayload;$zero.elementIds=@();$emptyDraft=Create $zero $pdf 'zero elements allowed';Assert 'zero relations persisted' (@($emptyDraft.elements).Count -eq 0)
    [void](Create (CopyPayload) $docx 'valid DOCX');[void](Create (CopyPayload) $png 'valid PNG');[void](Create (CopyPayload) $pdf 'same filename second upload')
    $edited=CopyPayload;$edited.title="阶段三-$tag-编辑";$edited.elementIds=@()
    $r=Request $teacher PUT "/teacher/resources/$id" $edited $null -Multipart;Status 'metadata edit without replacing attachment' $r 200
    Assert 'old attachment retained with zero elements' ($r.Json.data.fileOriginalName -eq $pdf.Name -and @($r.Json.data.elements).Count -eq 0)
    $edited.elementIds=@($e1,$e2)
    $r=Request $teacher PUT "/teacher/resources/$id" $edited $docx -Multipart;Status 'replace attachment' $r 200;Assert 'new file metadata' ($r.Json.data.fileOriginalName -eq $docx.Name -and @($r.Json.data.elements).Count -eq 2)
    $badFiles=@(@{Name='empty.pdf';Mime='application/pdf';Bytes=[byte[]]@()},@{Name='bad.exe';Mime='application/pdf';Bytes=$pdf.Bytes},@{Name='mime.pdf';Mime='image/png';Bytes=$pdf.Bytes},@{Name='fake.pdf';Mime='application/pdf';Bytes=[byte[]]@(1,2,3)},@{Name='../escape.pdf';Mime='application/pdf';Bytes=$pdf.Bytes},@{Name='..\escape.pdf';Mime='application/pdf';Bytes=$pdf.Bytes})
    foreach ($f in $badFiles) {
        Status "invalid upload $($f.Name)" (Request $teacher POST '/teacher/resources' (CopyPayload) $f -Multipart) 400
        Status "invalid replacement $($f.Name)" (Request $teacher PUT "/teacher/resources/$id" $edited $f -Multipart) 400
        Assert 'failed replacement retains current metadata' ((Request $teacher GET "/teacher/resources/$id").Json.data.fileOriginalName -eq $docx.Name)
    }
    $large=@{Name='large.pdf';Mime='application/pdf';Bytes=[byte[]]::new(20*1024*1024+1)}
    Status 'oversize upload' (Request $teacher POST '/teacher/resources' (CopyPayload) $large -Multipart) 413
    Status 'missing file' (Request $teacher POST '/teacher/resources' (CopyPayload) $null -Multipart) 400
    foreach ($query in @('page=0','size=101','status=APPROVED','courseId=-1','categoryId=0')) { Status "invalid list $query" (Request $teacher GET "/teacher/resources?$query") 400 }
    $query="keyword=$([uri]::EscapeDataString($tag))&courseId=$course&categoryId=$category&status=DRAFT&size=1"
    $list=Request $teacher GET "/teacher/resources?$query";Status 'combined filter first page' $list 200;Assert 'pagination and total' ($list.Json.data.total -eq $script:resources.Count -and @($list.Json.data.items).Count -eq 1)
    $next=Request $teacher GET "/teacher/resources?$query&page=2";Status 'second page' $next 200;Assert 'different page item' ($list.Json.data.items[0].id -ne $next.Json.data.items[0].id)
    Status 'owner soft delete' (Request $teacher DELETE "/teacher/resources/$id") 200
    Status 'soft deleted detail hidden' (Request $teacher GET "/teacher/resources/$id") 404
    Status 'repeat delete' (Request $teacher DELETE "/teacher/resources/$id") 404
    Assert 'soft deleted excluded from list' ($id -notin @((Request $teacher GET "/teacher/resources?keyword=$tag").Json.data.items|ForEach-Object id))
    Write-Host "TOTAL_ASSERTIONS_PASSED=$script:passed HTTP_REQUESTS=$script:requests TEST_TAG=$tag"
    Write-Host "BROWSER_FIXTURES course=$course category=$category element1=$e1 element2=$e2 KEEP=$KeepFixtures"
} finally {
    foreach ($id in $script:resources) { try { $r=Request $teacher DELETE "/teacher/resources/$id";if ($r.Status -notin @(200,404)) {Write-Warning "Resource cleanup failed $id"} } catch {Write-Warning "Resource cleanup request failed $id"} }
    if (-not $KeepFixtures) {foreach ($row in $script:rows) {try {[void](Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='INACTIVE'})}catch{Write-Warning 'Base fixture deactivation failed'}}}
    foreach ($ctx in $script:contexts) {$ctx.Client.Dispose()}
}
