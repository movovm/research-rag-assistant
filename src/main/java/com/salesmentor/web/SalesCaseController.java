package com.salesmentor.web;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.salescase.application.SalesCaseApplicationService;
import com.salesmentor.salescase.domain.SalesCase;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*;
import java.net.URI; import java.util.List;

@RestController @RequestMapping("/api/v1/cases")
public class SalesCaseController {
 private final SalesCaseApplicationService service; public SalesCaseController(SalesCaseApplicationService service){this.service=service;}
 @PostMapping public ResponseEntity<ImportResponse> importCase(@Valid @RequestBody ImportRequest r){SalesCase c=service.importCase(new SalesCase(null,r.externalKey(),r.title(),r.sourceType(),r.sourceUri(),r.industry(),r.salesStage(),r.customerRole(),r.content(),SalesCase.Status.IMPORTED,null,0,null,null));return ResponseEntity.accepted().location(URI.create("/api/v1/cases/"+c.id())).body(new ImportResponse(c.id(),c.status(),true));}
 @GetMapping("/{id}") public SalesCase get(@PathVariable Long id){return service.get(id);}
 @PostMapping("/{id}/extraction:retry") public ResponseEntity<Void> retry(@PathVariable Long id){service.retry(id);return ResponseEntity.accepted().build();}
 @GetMapping("/{id}/experiences") public List<ExperienceUnit> experiences(@PathVariable Long id){return service.experiences(id);}
 public record ImportResponse(Long caseId, SalesCase.Status status, boolean extractionSubmitted){}
 public record ImportRequest(@Size(max=64) String externalKey,@NotBlank @Size(max=200) String title,SalesCase.SourceType sourceType,String sourceUri,@Size(max=64) String industry,SalesCase.SalesStage salesStage,@Size(max=64) String customerRole,@NotBlank @Size(max=20000) String content){public ImportRequest{if(sourceType==null)sourceType=SalesCase.SourceType.USER_PROVIDED;}}
}
