package com.learn.springai.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.learn.springai.model.ChatMessage;
import com.learn.springai.model.Conversation;
import com.learn.springai.repository.ChatMessageRepository;
import com.learn.springai.repository.ConversationRepository;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;

@Service
@Slf4j
public class DocumentProcessingService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;

    @Value("${tessdata.path:/opt/homebrew/Cellar/tesseract/5.5.0_1/share/tessdata}")
    private String tessdataPath;

    public DocumentProcessingService(
            @Qualifier("routerChatClient") ChatClient chatClient,
            VectorStore vectorStore,
            ChatMessageRepository chatMessageRepository,
            ConversationRepository conversationRepository) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
    }

    private Tesseract createTesseractSafe() {
        try {
            System.setProperty("jna.library.path", "/opt/homebrew/lib:/usr/local/lib:/usr/lib");
            Tesseract tesseract = new Tesseract();
            if (tessdataPath != null && !tessdataPath.isBlank()) {
                tesseract.setDatapath(tessdataPath);
            }
            tesseract.setLanguage("eng");
            return tesseract;
        } catch (Throwable t) {
            log.warn("Tesseract OCR initialization failed or not available on host: {}", t.getMessage());
            return null;
        }
    }

    public String extractText(byte[] bytes, String contentType) {
        if (contentType == null) {
            contentType = "";
        }
        log.info("Processing file content type: {}", contentType);

        try {
            String lower = contentType.toLowerCase();
            if (lower.contains("pdf")) {
                return extractTextFromPdf(bytes);
            } else if (lower.contains("image")) {
                return extractTextFromImage(bytes);
            } else if (lower.contains("text") || lower.contains("csv") || lower.contains("json")) {
                return new String(bytes, StandardCharsets.UTF_8).trim();
            } else {
                return extractTextWithTika(bytes);
            }
        } catch (Throwable t) {
            log.error("Failed to extract text using standard extractors, falling back to Tika/String", t);
            try {
                return extractTextWithTika(bytes);
            } catch (Throwable fallbackErr) {
                return new String(bytes, StandardCharsets.UTF_8).trim();
            }
        }
    }

    private String extractTextFromPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text != null && text.trim().length() > 20) {
                log.info("Extracted PDF text via PDFBox stripper (length={})", text.trim().length());
                return text.trim();
            }

            log.info("PDF stripper returned very short text. Attempting Tesseract OCR fallback...");
            Tesseract tesseract = createTesseractSafe();
            if (tesseract != null) {
                try {
                    PDFRenderer renderer = new PDFRenderer(document);
                    StringBuilder sb = new StringBuilder();
                    int maxPages = Math.min(document.getNumberOfPages(), 5);
                    for (int i = 0; i < maxPages; i++) {
                        java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150);
                        sb.append(tesseract.doOCR(img)).append("\n");
                    }
                    String ocrText = sb.toString().trim();
                    if (!ocrText.isBlank()) {
                        return ocrText;
                    }
                } catch (Throwable ocrErr) {
                    log.warn("PDF Tesseract OCR failed: {}", ocrErr.getMessage());
                }
            }
            return extractTextWithTika(bytes);
        } catch (Throwable e) {
            log.warn("PDFBox parsing failed, falling back to Apache Tika: {}", e.getMessage());
            return extractTextWithTika(bytes);
        }
    }

    private String extractTextFromImage(byte[] bytes) {
        Tesseract tesseract = createTesseractSafe();
        if (tesseract != null) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(bais);
                if (img != null) {
                    log.info("Extracting text from image via Tesseract OCR");
                    String ocrResult = tesseract.doOCR(img).trim();
                    if (!ocrResult.isBlank()) {
                        return ocrResult;
                    }
                }
            } catch (Throwable e) {
                log.warn("Tesseract OCR on image failed, falling back to Tika: {}", e.getMessage());
            }
        }
        return extractTextWithTika(bytes);
    }

    private String extractTextWithTika(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            Tika tika = new Tika();
            log.info("Extracting text via Apache Tika");
            String result = tika.parseToString(bais);
            return result != null ? result.trim() : "";
        } catch (Throwable e) {
            log.warn("Apache Tika extraction failed: {}", e.getMessage());
            return "";
        }
    }

    public boolean checkTravelRelevance(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            String checkSnippet = text.substring(0, Math.min(text.length(), 4000));
            String response = chatClient.prompt()
                    .user("Analyze the following text. Does it contain travel-related information like flight bookings, hotel reservations, travel plans, itineraries, tourism sights, visas, traveler details, packing lists, transport, or travel preferences? Respond with exactly 'YES' or 'NO' and nothing else.\n\nText:\n" + checkSnippet)
                    .call()
                    .content();
            log.info("LLM relevance verification result: {}", response);
            return response != null && response.trim().toUpperCase().startsWith("YES");
        } catch (Exception e) {
            log.warn("Travel relevance check failed, accepting document by default: {}", e.getMessage());
            return true;
        }
    }

    public void indexToVectorDB(String text, String conversationId, String sourceKey) {
        if (text == null || text.isBlank()) return;

        try {
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(200)
                    .withMaxNumChunks(400)
                    .build();

            Document doc = new Document(text, Map.of("conversation_id", conversationId, "source", sourceKey));
            List<Document> chunks = splitter.split(List.of(doc));

            int chunkIndex = 0;
            for (Document chunk : chunks) {
                chunk.getMetadata().put("chunk_index", chunkIndex++);
                chunk.getMetadata().put("conversation_id", conversationId);
                chunk.getMetadata().put("source", sourceKey);
            }

            log.info("Indexing {} document chunks to VectorStore for conversation: {}", chunks.size(), conversationId);
            vectorStore.add(chunks);
        } catch (Exception e) {
            log.error("Failed to index document chunks into vectorStore for conversation: {}", conversationId, e);
        }
    }

    public void addContextToConversation(Conversation conversation, String filename, String text) {
        try {
            List<ChatMessage> existing = chatMessageRepository
                    .findByConversationIdAndDeletedFalseOrderBySequenceNumberAsc(conversation.getId());
            int nextSeq = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getSequenceNumber() + 1;

            String summary = generateConciseSummary(filename, text);

            ChatMessage userMessage = ChatMessage.builder()
                    .conversation(conversation)
                    .role("USER")
                    .content("System Note: User uploaded a file \"" + filename + "\". Key details extracted:\n\n" + summary)
                    .sequenceNumber(nextSeq)
                    .messageTimestamp(LocalDateTime.now())
                    .deleted(false)
                    .build();

            chatMessageRepository.save(userMessage);

            ChatMessage assistantMessage = ChatMessage.builder()
                    .conversation(conversation)
                    .role("ASSISTANT")
                    .content("Thank you! I have added the information from **" + filename + "** to your trip configuration.\n\n" +
                             "**Extracted Details:**\n" + summary + "\n\n" +
                             "Let me know if you would like to adjust any dates, budget, or generate your daily itinerary!")
                    .sequenceNumber(nextSeq + 1)
                    .messageTimestamp(LocalDateTime.now().plusSeconds(1))
                    .deleted(false)
                    .build();

            chatMessageRepository.save(assistantMessage);
            log.info("Saved user document note (seq={}) and assistant confirmation (seq={})", nextSeq, nextSeq + 1);
        } catch (Exception e) {
            log.error("Failed to add uploaded file context to conversation: {}", conversation.getId(), e);
        }
    }

    private String generateConciseSummary(String filename, String text) {
        try {
            String snippet = text.length() > 3000 ? text.substring(0, 3000) + "\n...[truncated]..." : text;
            
            String prompt = String.format(
                "<system_instructions>\n" +
                "  <role>You are a travel document summarizer.</role>\n" +
                "  <task>Write a concise summary (maximum 2-3 sentences or bullet points) of key details extracted from the travel document/ticket \"%s\".</task>\n" +
                "  <guidelines>Only include important travel details like passenger name, flight number, check-in date/time, hotel name, booking reference, or travel source/destination. Do not include greeting or conversational filler.</guidelines>\n" +
                "</system_instructions>\n\nDocument Content:\n%s", 
                filename, snippet
            );
            
            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (summary != null && !summary.isBlank()) {
                return summary.trim();
            }
        } catch (Exception e) {
            log.error("Failed to generate document summary, using fallback snippet", e);
        }
        
        return text.length() > 250 ? text.substring(0, 250) + "..." : text;
    }
}
