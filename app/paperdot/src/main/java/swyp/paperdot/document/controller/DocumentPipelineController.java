package swyp.paperdot.document.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swyp.paperdot.document.service.DocumentPipelineService;

import java.util.Map;

@Slf4j
@Tag(name = "문서 처리 파이프라인", description = "문서의 전체 처리 과정을 관리하는 API")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentPipelineController {

    private final DocumentPipelineService documentPipelineService;

    @Operation(summary = "문서 처리 파이프라인 실행", description = "특정 문서 ID에 대해 텍스트 추출, 번역, 저장까지의 전체 파이프라인을 비동기적으로 실행합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "파이프라인 처리가 성공적으로 시작됨"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 문서 ID일 경우"),
            @ApiResponse(responseCode = "500", description = "파이프라인 실행 중 서버 내부 오류 발생")
    })
    @PostMapping("/{documentId}/process")
    public ResponseEntity<Map<String, String>> processDocument(
            @Parameter(description = "처리할 문서의 ID", required = true) @PathVariable Long documentId,
            @Parameter(description = "기존에 처리된 'doc_units'가 있을 경우 덮어쓸지 여부. 기본값은 false.")
            @RequestParam(defaultValue = "false") boolean overwrite
    ) {
        log.info("API 요청: documentId {} 문서를 처리 요청 받음. Overwrite: {}", documentId, overwrite);
        // 파이프라인 서비스를 비동기적으로 호출합니다.
        documentPipelineService.processDocumentAsync(documentId, overwrite);
        // 작업이 시작되었음을 즉시 클라이언트에 알립니다.
        return ResponseEntity.accepted().body(Map.of("message", "Document processing initiated for documentId: " + documentId));
    }

    @Operation(summary = "문서 번역 쌍 조회", description = "특정 문서의 원문-번역 문장 쌍을 1:1로 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "번역 쌍 조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 문서 ID일 경우 또는 번역 데이터가 없을 경우")
    })
    @GetMapping("/{documentId}/translation-pairs")
    public ResponseEntity<java.util.List<swyp.paperdot.document.dto.DocumentTranslationPairResponse>> getTranslationPairs(
            @Parameter(description = "번역 쌍을 조회할 문서의 ID", required = true) @PathVariable Long documentId
    ) {
        log.info("API 요청: documentId {} 번역 쌍 조회 요청 받음.", documentId);
        java.util.List<swyp.paperdot.document.dto.DocumentTranslationPairResponse> translationPairs =
                documentPipelineService.getTranslationPairsForDocument(documentId);

        if (translationPairs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(translationPairs);
    }
}
