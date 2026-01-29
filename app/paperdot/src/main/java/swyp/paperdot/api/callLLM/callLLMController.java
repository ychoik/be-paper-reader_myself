package swyp.paperdot.api.callLLM;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * LLM(Large Language Model) 상호 작용과 관련된 요청을 처리하기 위한 REST 컨트롤러입니다.
 * 구성된 LLM에 프롬프트를 보내고 응답을 받기 위한 엔드포인트를 제공합니다.
 */
@RestController
@RequestMapping("/api/llm") // 모든 LLM 관련 API 엔드포인트의 기본 경로
public class callLLMController {

    private final callLLMService callLLMService; // 주입된 LLM 서비스

    /**
     * CallLLMController의 생성자로, CallLLMService를 주입합니다.
     * @param callLLMService LLM API 호출을 담당하는 서비스.
     */
    @Autowired
    public callLLMController(callLLMService callLLMService) {
        this.callLLMService = callLLMService;
    }

    /**
     * /api/llm/chat 엔드포인트에 대한 POST 요청을 처리합니다.
     * 요청 본문으로 프롬프트를 받아 LLM의 채팅 응답을 반환합니다.
     * @param prompt LLM에 보낼 사용자의 입력 문자열.
     * @return LLM의 응답 문자열.
     */
    @PostMapping("/chat")
    public String chat(@RequestBody String prompt) {
        // LLM으로부터 응답을 받기 위해 프롬프트를 callLLMService에 위임합니다.
        return callLLMService.getChatResponse(prompt);
    }

    /**
     * /api/llm/chat-pdf 엔드포인트에 대한 POST 요청을 처리합니다.
     * 업로드된 PDF 파일의 텍스트를 추출하여 LLM의 채팅 응답을 반환합니다.
     * @param file 업로드된 PDF 파일.
     * @return LLM의 응답 문자열.
     * @throws IOException 파일 처리 중 오류가 발생할 경우.
     */
    @PostMapping("/chat-pdf")
    public String chatPdf(@RequestParam("file") MultipartFile file) throws IOException {
        // PDF 파일에서 텍스트를 추출하고 LLM 응답을 받기 위해 callLLMService에 위임합니다.
        return callLLMService.getChatResponseFromPdf(file);
    }
}
