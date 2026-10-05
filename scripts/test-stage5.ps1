<# Local-only stage 5 HTTP + MySQL verification. Approved fixtures are retained;
   no new production delete/downlisting API is introduced. Local credentials are never printed. #>
param([string]$BaseUrl='http://127.0.0.1:5173',[switch]$KeepFixtures)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.Drawing
$script:passed=0; $script:requests=0; $script:contexts=@(); $script:rows=@(); $script:ids=@(); $otherDraftId=0
$tag=[guid]::NewGuid().ToString('N').Substring(0,8)
$project=Split-Path $PSScriptRoot -Parent
function Context {
    $handler=[Net.Http.HttpClientHandler]::new();$handler.CookieContainer=[Net.CookieContainer]::new()
    $client=[Net.Http.HttpClient]::new($handler);$client.Timeout=[TimeSpan]::FromSeconds(30)
    $ctx=@{Client=$client;Csrf=$null;Header=$null};$script:contexts+=,$ctx;return $ctx
}
function Request($ctx,[string]$method,[string]$path,$body=$null,$file=$null,[switch]$Multipart,[switch]$NoCsrf) {
    $script:requests++;$req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($method),"$BaseUrl/api$path")
    if($method -notin @('GET','HEAD') -and -not $NoCsrf -and $ctx.Csrf){[void]$req.Headers.TryAddWithoutValidation($ctx.Header,$ctx.Csrf)}
    if($Multipart){$content=[Net.Http.MultipartFormDataContent]::new();$content.Add([Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress -Depth 10),[Text.Encoding]::UTF8,'application/json'),'metadata');$part=[Net.Http.ByteArrayContent]::new([byte[]]$file.Bytes);$part.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new($file.Mime);$content.Add($part,'file',$file.Name);$req.Content=$content}
    elseif($null -ne $body){$req.Content=[Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress -Depth 10),[Text.Encoding]::UTF8,'application/json')}
    $resp=$null
    try{$resp=$ctx.Client.SendAsync($req).GetAwaiter().GetResult();$bytes=$resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult();$json=$null;try{$json=[Text.Encoding]::UTF8.GetString($bytes)|ConvertFrom-Json}catch{};return @{Status=[int]$resp.StatusCode;Json=$json;Bytes=$bytes;Disposition=[string]$resp.Content.Headers.ContentDisposition;Mime=[string]$resp.Content.Headers.ContentType;Cache=[string]$resp.Headers.CacheControl;NoSniff=($resp.Headers.GetValues('X-Content-Type-Options') -join ',')}}
    finally{if($resp){$resp.Dispose()};$req.Dispose()}
}
function Assert([string]$name,[bool]$ok){if(-not $ok){throw "FAIL: $name"};$script:passed++;Write-Host "PASS: $name"}
function Status([string]$name,$r,[int]$expected){Assert "$name HTTP $($r.Status)" ($r.Status -eq $expected);Assert "$name unified" ([bool]$r.Json.code -and [bool]$r.Json.message -and ($expected -ge 300 -or $r.Json.code -eq 'OK'))}
function Csrf($ctx){$r=Request $ctx GET '/auth/csrf';if($r.Status -ne 200){throw 'CSRF unavailable'};$ctx.Csrf=$r.Json.data.token;$ctx.Header=$r.Json.data.headerName}
function Login($name,$password){$ctx=Context;Csrf $ctx;Status "$name login" (Request $ctx POST '/auth/login' @{username=$name;password=$password}) 200;Csrf $ctx;return $ctx}
function SQL([string]$query){
    $local=[IO.File]::ReadAllText((Join-Path $project 'backend/config/application-local.yml'))
    $dbUser=[regex]::Match($local,'(?m)^\s*username:\s*["'']?([^\s"'']+)').Groups[1].Value
    $dbPassword=[regex]::Match($local,'(?m)^\s*password:\s*["'']?([^\s"'']+)').Groups[1].Value
    $previous=$env:MYSQL_PWD;$env:MYSQL_PWD=$dbPassword
    try{$result=@(& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --port=3306 --user=$dbUser --default-character-set=utf8mb4 --batch --skip-column-names '--database=management-system' --execute=$query);if($LASTEXITCODE -ne 0){throw 'Local MySQL verification failed'};return $result}
    finally{if($null -eq $previous){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previous};$dbPassword=$null;$local=$null}
}
function Count($table,$id){return [int](SQL "SELECT COUNT(*) FROM $table WHERE resource_id=$id;")}
function PDF {
    $stream='BT /F1 16 Tf 30 100 Td (Stage 5 published resource) Tj ET'
    $parts=@('%PDF-1.4',"1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj","2 0 obj`n<< /Type /Pages /Kids [3 0 R] /Count 1 >>`nendobj","3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>`nendobj","4 0 obj`n<< /Length $([Text.Encoding]::ASCII.GetByteCount($stream)) >>`nstream`n$stream`nendstream`nendobj","5 0 obj`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>`nendobj")
    $text=$parts[0]+"`n";$offsets=@();foreach($p in $parts[1..5]){$offsets+=[Text.Encoding]::ASCII.GetByteCount($text);$text+=$p+"`n"}
    $xref=[Text.Encoding]::ASCII.GetByteCount($text);$text+="xref`n0 6`n0000000000 65535 f `n";foreach($o in $offsets){$text+=('{0:D10} 00000 n ' -f $o)+"`n"};$text+="trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n";return ,[Text.Encoding]::ASCII.GetBytes($text)
}
function PNG {
    $bitmap=[Drawing.Bitmap]::new(320,180);$graphics=[Drawing.Graphics]::FromImage($bitmap);$memory=[IO.MemoryStream]::new()
    try{$graphics.Clear([Drawing.Color]::LightBlue);$graphics.FillRectangle([Drawing.Brushes]::Teal,20,20,280,140);$font=[Drawing.Font]::new('Arial',18);try{$graphics.DrawString('Stage 5 resource',[Drawing.Font]$font,[Drawing.Brushes]::White,35,75)}finally{$font.Dispose()};$bitmap.Save($memory,[Drawing.Imaging.ImageFormat]::Png);return ,$memory.ToArray()}
    finally{$graphics.Dispose();$bitmap.Dispose();$memory.Dispose()}
}
function DOCX {
    $memory=[IO.MemoryStream]::new();$zip=[IO.Compression.ZipArchive]::new($memory,[IO.Compression.ZipArchiveMode]::Create,$true)
    try{foreach($pair in @(@{Name='[Content_Types].xml';Text='<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'},@{Name='_rels/.rels';Text='<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'},@{Name='word/document.xml';Text='<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Stage 5 local Office resource</w:t></w:r></w:p></w:body></w:document>'})){$entry=$zip.CreateEntry($pair.Name);$writer=[IO.StreamWriter]::new($entry.Open(),[Text.UTF8Encoding]::new($false));try{$writer.Write($pair.Text)}finally{$writer.Dispose()}}}
    finally{$zip.Dispose()};$bytes=$memory.ToArray();$memory.Dispose();return ,$bytes
}
function BaseRow($type,$suffix){$p=@{name="S5-$tag-$suffix";description='第五阶段本地测试'};if($type -eq 'courses'){$p.courseCode="S5-$tag"};$r=Request $admin POST "/$type" $p;Status 'base fixture' $r 201;$script:rows+=@{Type=$type;Id=$r.Json.data.id};return [long]$r.Json.data.id}
function Create($suffix,$file){$p=@{title="资源中心-$tag-$suffix";description="课程思政数字资源$tag";courseId=$course;categoryId=$category;elementIds=@($element)};$r=Request $teacher POST '/teacher/resources' $p $file -Multipart;Status "create $suffix" $r 201;$script:ids+=,[long]$r.Json.data.id;return [long]$r.Json.data.id}
function Publish($id){Status 'submit fixture' (Request $teacher POST "/teacher/resources/$id/submit") 200;Status 'approve fixture' (Request $admin POST "/admin/resource-reviews/$id/approve" @{submissionNo=1}) 200}
try{
    $admin=Login 'dev_admin' $(if($env:STAGE5_ADMIN_PASSWORD){$env:STAGE5_ADMIN_PASSWORD}else{'AdminDev#2026'})
    $teacher=Login 'dev_teacher' $(if($env:STAGE5_TEACHER_PASSWORD){$env:STAGE5_TEACHER_PASSWORD}else{'TeacherDev#2026'})
    $student=Login 'dev_student' $(if($env:STAGE5_STUDENT_PASSWORD){$env:STAGE5_STUDENT_PASSWORD}else{'StudentDev#2026'})
    $otherTeacher=Login 'dev_teacher_b' $(if($env:STAGE5_OTHER_TEACHER_PASSWORD){$env:STAGE5_OTHER_TEACHER_PASSWORD}else{'TeacherBDev#2026'})
    $anon=Context;Csrf $anon
    $studentId=[long](Request $student GET '/auth/me').Json.data.id;$teacherId=[long](Request $teacher GET '/auth/me').Json.data.id
    $course=BaseRow courses '课程';$category=BaseRow resource-categories '分类';$element=BaseRow ideological-elements '元素'
    $pdf=@{Name='课程思政案例.pdf';Mime='application/pdf';Bytes=(PDF)};$png=@{Name='教学资源图片.png';Mime='image/png';Bytes=(PNG)};$docx=@{Name='课程思政教学案例.docx';Mime='application/vnd.openxmlformats-officedocument.wordprocessingml.document';Bytes=(DOCX)}
    $pdfId=Create 'PDF' $pdf;Publish $pdfId;$pngId=Create '图片' $png;Publish $pngId;$officeId=Create 'Office' $docx;Publish $officeId
    $draftId=Create '草稿' $pdf;$pendingId=Create '待审核' $pdf;Status 'pending fixture' (Request $teacher POST "/teacher/resources/$pendingId/submit") 200
    $rejectedId=Create '驳回' $pdf;Status 'rejected submit' (Request $teacher POST "/teacher/resources/$rejectedId/submit") 200;Status 'rejected fixture' (Request $admin POST "/admin/resource-reviews/$rejectedId/reject" @{submissionNo=1;reason='本地隔离测试'}) 200
    $otherDraft=Request $otherTeacher POST '/teacher/resources' @{title="资源中心-$tag-其他教师草稿";description='其他教师未提交资源';courseId=$course;categoryId=$category;elementIds=@($element)} $pdf -Multipart
    Status 'other teacher draft fixture' $otherDraft 201;$otherDraftId=[long]$otherDraft.Json.data.id
    foreach($suffix in @('','/preview','/download')){Status 'other teacher draft public isolation' (Request $teacher GET "/resources/$otherDraftId$suffix") 404}
    Status 'other teacher draft favorite isolation' (Request $teacher POST "/resources/$otherDraftId/favorite") 404
    $deletedId=Create '已删除' $pdf;Publish $deletedId
    # Direct SQL is confined to disposable test fixtures; it does not add a production downlisting API.
    [void](SQL "UPDATE teaching_resource SET deleted_at=CURRENT_TIMESTAMP(3) WHERE id=$deletedId AND title LIKE '%$tag%';")
    foreach($id in @($draftId,$pendingId,$rejectedId,$deletedId)){[void](SQL "INSERT INTO favorite(user_id,resource_id,active) VALUES ($studentId,$id,1) ON DUPLICATE KEY UPDATE active=1;")}
    foreach($actor in @($student,$teacher)){
        $r=Request $actor GET "/resources?keyword=$tag&courseId=$course&categoryId=$category&elementId=$element";Status 'combined published list' $r 200
        Assert 'only three published fixtures' ($r.Json.data.total -eq 3 -and @($r.Json.data.items).Count -eq 3)
        Assert 'public DTO excludes private workflow and file keys' (-not ($r.Json|ConvertTo-Json -Depth 20).Contains('fileStorageKey') -and -not ($r.Json|ConvertTo-Json -Depth 20).Contains('storageKey') -and -not ($r.Json|ConvertTo-Json -Depth 20).Contains('auditRecords'))
        $r=Request $actor GET "/resources?keyword=$tag&status=DRAFT";Status 'client status cannot broaden visibility' $r 200;Assert 'status parameter ignored safely' ($r.Json.data.total -eq 3)
        foreach($id in @($draftId,$pendingId,$rejectedId,$deletedId)){
            foreach($suffix in @('','/preview','/download')){Status "invisible resource $id $suffix" (Request $actor GET "/resources/$id$suffix") 404}
            Status 'invisible favorite denied' (Request $actor POST "/resources/$id/favorite") 404
        }
    }
    Assert 'list and isolation requests do not browse' ((Count browse_record $pdfId) -eq 0 -and (Count browse_record $draftId) -eq 0)
    $r=Request $student GET '/favorites';Status 'favorites isolation' $r 200;Assert 'hidden fixtures absent from favorites' (@($r.Json.data.items|Where-Object {$_.id -in @($draftId,$pendingId,$rejectedId,$deletedId)}).Count -eq 0)
    foreach($path in @('/resources','/favorites',"/resources/$pdfId","/resources/$pdfId/preview","/resources/$pdfId/download")){Status 'anonymous public use denied' (Request $anon GET $path) 401;Status 'admin public use denied' (Request $admin GET $path) 403}
    Status 'anonymous favorite with CSRF' (Request $anon POST "/resources/$pdfId/favorite") 401;Status 'admin favorite denied' (Request $admin POST "/resources/$pdfId/favorite") 403
    foreach($q in @('page=0','size=101','courseId=0','categoryId=-1','elementId=0')){Status "invalid filter $q" (Request $student GET "/resources?$q") 400}
    Status 'invalid ID' (Request $student GET '/resources/0') 400;Status 'malformed ID' (Request $student GET '/resources/not-a-number') 400;Status 'missing ID' (Request $student GET '/resources/9223372036854775807') 404
    $a=Request $student GET "/resources?keyword=$tag&size=1";$b=Request $student GET "/resources?keyword=$tag&size=1&page=2";Status 'page 1' $a 200;Status 'page 2' $b 200;Assert 'stable distinct page resources' ($a.Json.data.total -eq 3 -and $a.Json.data.items[0].id -ne $b.Json.data.items[0].id)
    Status 'description keyword' (Request $student GET "/resources?keyword=$([uri]::EscapeDataString("数字资源$tag"))") 200
    $r=Request $student GET "/resources/$pdfId`?userId=1";Status 'student detail' $r 200;Assert 'one detail one browse' ((Count browse_record $pdfId) -eq 1)
    Assert 'browse caller cannot spoof user' ([int](SQL "SELECT COUNT(*) FROM browse_record WHERE resource_id=$pdfId AND user_id<>$studentId;") -eq 0)
    $r=Request $teacher GET "/resources/$pdfId";Status 'teacher detail' $r 200;Assert 'both session user IDs recorded' ([int](SQL "SELECT COUNT(DISTINCT user_id) FROM browse_record WHERE resource_id=$pdfId;") -eq 2)
    foreach($entry in @(@{Id=$pdfId;File=$pdf},@{Id=$pngId;File=$png})){
        $r=Request $student GET "/resources/$($entry.Id)/preview";Assert 'authorized preview bytes and headers' ($r.Status -eq 200 -and $r.Mime -eq $entry.File.Mime -and $r.Disposition.StartsWith('inline') -and $r.NoSniff -eq 'nosniff' -and [Convert]::ToBase64String($r.Bytes) -eq [Convert]::ToBase64String($entry.File.Bytes))
    }
    Status 'Office preview explanation' (Request $student GET "/resources/$officeId/preview") 400
    [void](Request $admin GET "/admin/resource-reviews/$pdfId/attachment");[void](Request $admin GET "/admin/resource-reviews/$pdfId/attachment?download=true")
    Assert 'preview and admin audit do not add usage' ((Count browse_record $pdfId) -eq 2 -and (Count download_record $pdfId) -eq 0)
    foreach($actor in @($student,$teacher)){
        Status 'favorite missing CSRF' (Request $actor POST "/resources/$pdfId/favorite" -NoCsrf) 403
        Status 'favorite approved' (Request $actor POST "/resources/$pdfId/favorite" @{userId=1;resourceId=$draftId}) 200
        Status 'duplicate favorite idempotent' (Request $actor POST "/resources/$pdfId/favorite") 200
        foreach($entry in @(@{Id=$pdfId;File=$pdf},@{Id=$officeId;File=$docx})){
            $r=Request $actor GET "/resources/$($entry.Id)/download?userId=1";Assert 'download bytes type Chinese filename and safe headers' ($r.Status -eq 200 -and $r.Disposition.StartsWith('attachment') -and $r.Disposition.Contains('filename*=UTF-8') -and $r.Mime -eq $entry.File.Mime -and $r.Cache -eq 'no-store' -and $r.NoSniff -eq 'nosniff' -and [Convert]::ToBase64String($r.Bytes) -eq [Convert]::ToBase64String($entry.File.Bytes))
        }
    }
    Assert 'two users each one unique favorite' ((Count favorite $pdfId) -eq 2)
    Assert 'favorite caller cannot spoof user' ([int](SQL "SELECT COUNT(*) FROM favorite WHERE resource_id=$pdfId AND user_id NOT IN ($studentId,$teacherId);") -eq 0)
    Assert 'downloads use session users' ([int](SQL "SELECT COUNT(*) FROM download_record WHERE resource_id=$pdfId AND user_id IN ($studentId,$teacherId);") -eq 2)
    Assert 'download caller cannot spoof user' ([int](SQL "SELECT COUNT(*) FROM download_record WHERE resource_id=$pdfId AND user_id NOT IN ($studentId,$teacherId);") -eq 0)
    Assert 'favorites/downloads do not add browse' ((Count browse_record $pdfId) -eq 2)
    Status 'cancel favorite' (Request $student DELETE "/resources/$pdfId/favorite") 200;Status 'repeat cancel idempotent' (Request $student DELETE "/resources/$pdfId/favorite") 200
    Assert 'cancel retains inactive relation' ([int](SQL "SELECT COUNT(*) FROM favorite WHERE user_id=$studentId AND resource_id=$pdfId AND active=0;") -eq 1)
    $r=Request $student GET '/favorites';Status 'after cancel favorites list' $r 200;Assert 'cancelled resource absent' (@($r.Json.data.items|Where-Object {$_.id -eq $pdfId}).Count -eq 0)
    Status 'favorite again' (Request $student POST "/resources/$pdfId/favorite") 200;Assert 'reactivation not duplicate row' ((Count favorite $pdfId) -eq 2)
    $r=Request $student HEAD "/resources/$pdfId";Assert 'HEAD detail success without event' ($r.Status -eq 200 -and (Count browse_record $pdfId) -eq 2)
    $r=Request $student HEAD "/resources/$pdfId/download";Assert 'HEAD download success without event' ($r.Status -eq 200 -and (Count download_record $pdfId) -eq 2)
    $key=[string](SQL "SELECT file_storage_key FROM teaching_resource WHERE id=$pdfId;");if($key -notmatch '^[a-f0-9-]{36}\.pdf$'){throw 'Invalid test storage key'}
    $storage=[IO.Path]::GetFullPath((Join-Path $project 'backend/uploads/resources'));$file=Join-Path $storage $key;$held=Join-Path $storage ($key+'.stage5-test-held')
    [IO.File]::Move($file,$held)
    try{Status 'missing real file download' (Request $student GET "/resources/$pdfId/download") 400;Assert 'failed download not counted' ((Count download_record $pdfId) -eq 2)}finally{[IO.File]::Move($held,$file)}
    Write-Host "TOTAL_ASSERTIONS_PASSED=$script:passed HTTP_REQUESTS=$script:requests TEST_TAG=$tag PDF_ID=$pdfId PNG_ID=$pngId OFFICE_ID=$officeId"
    Write-Host "BROWSER_BASE course=$course category=$category element=$element KEEP=$KeepFixtures"
}finally{
    $checks=$script:requests
    foreach($id in $script:ids){try{$r=Request $teacher GET "/teacher/resources/$id";if($r.Json.data.status -in @('DRAFT','REJECTED')){[void](Request $teacher DELETE "/teacher/resources/$id")}}catch{Write-Warning "Fixture retained: $id"}}
    if($otherDraftId){try{[void](Request $otherTeacher DELETE "/teacher/resources/$otherDraftId")}catch{Write-Warning "Other teacher fixture retained: $otherDraftId"}}
    if(-not $KeepFixtures){foreach($row in $script:rows){try{[void](Request $admin PATCH "/$($row.Type)/$($row.Id)/status" @{status='INACTIVE'})}catch{Write-Warning 'Base fixture retained'}}}
    Write-Host "CLEANUP_HTTP_REQUESTS=$($script:requests-$checks)";foreach($ctx in $script:contexts){$ctx.Client.Dispose()}
}
