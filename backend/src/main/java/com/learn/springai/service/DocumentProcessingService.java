package com.learn.springai.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;

@Service
@Slf4j
public class DocumentProcessingService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;

    @Value("${tessdata.path}")
    private String tessdataPath;

    public DocumentProcessingService(
            @Qualifier("groqChatClient") ChatClient chatClient,
            VectorStore vectorStore,
            ChatMessageRepository chatMessageRepository,
            ConversationRepository conversationRepository) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
    }

    private Tesseract getTesseractInstance() {
        System.setProperty("jna.library.path", "/opt/homebrew/lib:/usr/local/lib");
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("eng");
        return tesseract;
    }

    public String extractText(byte[] bytes, String contentType) throws Exception {
        if (contentType == null) {
            contentType = "";
        }
        log.info("Processing file content type: {}", contentType);

        if (contentType.toLowerCase().contains("pdf")) {
            return extractTextFromPdf(bytes);
        } else if (contentType.toLowerCase().contains("image")) {
            return extractTextFromImage(bytes);
        } else {
            return extractTextWithTika(bytes);
        }
    }

    private String extractTextFromPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text != null && text.trim().length() > 50) {
                log.info("Extracted PDF text via PDFBox stripper");
                return text.trim();
            }

            log.info("PDF stripper returned very short or empty text. Attempting Tesseract OCR...");
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();
            Tesseract tesseract = getTesseractInstance();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150);
                sb.append(tesseract.doOCR(img)).append("\n");
            }
            return sb.toString().trim();
        }
    }

    private String extractTextFromImage(byte[] bytes) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(bais);
            if (img == null) {
                throw new IllegalArgumentException("Failed to read image bytes");
            }
            Tesseract tesseract = getTesseractInstance();
            log.info("Extracting text from image via Tesseract OCR");
            return tesseract.doOCR(img).trim();
        }
    }

    private String extractTextWithTika(byte[] bytes) throws Exception {
        Tika tika = new Tika();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            log.info("Extracting text via Apache Tika");
            return tika.parseToString(bais).trim();
        }
    }

    public boolean checkTravelRelevance(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String checkSnippet = text.substring(0, Math.min(text.length(), 4000));
        String response = chatClient.prompt()
                .user("Analyze the following text. Does it contain travel-related information like flight bookings, hotel reservations, travel plans, itineraries, tourism sights, visas, traveler details, packing lists, transport, or travel preferences? Respond with exactly 'YES' or 'NO' and nothing else.\n\nText:\n" + checkSnippet)
                .call()
                .content();
        log.info("LLM relevance verification result: {}", response);
        return response != null && response.trim().toUpperCase().startsWith("YES");
    }

    public void indexToVectorDB(String text, String conversationId, String sourceKey) {
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
    }

    public void addContextToConversation(Conversation conversation, String filename, String text) {
        // Find current message sequence max
        List<ChatMessage> existing = chatMessageRepository
                .findByConversationIdAndDeletedFalseOrderBySequenceNumberAsc(conversation.getId());
        int nextSeq = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getSequenceNumber() + 1;

        // Generate a concise summary of the document to avoid massive token overhead in prompt memory
        String summary = generateConciseSummary(filename, text);

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .role("USER")
                .content("System Note: User uploaded a file \"" + filename + "\". Key details extracted:\n\n" + summary)
                .sequenceNumber(nextSeq)
                .messageTimestamp(LocalDateTime.now())
                .deleted(false)
                .build();

        chatMessageRepository.save(message);
        log.info("Saved summarized user document context as user message sequence: {}", nextSeq);
    }

    private String generateConciseSummary(String filename, String text) {
        try {
            // Keep the text snippet sent to LLM under a reasonable size (e.g. 3000 chars) to prevent rate limits
            String snippet = text.length() > 3000 ? text.substring(0, 3000) + "\n...[truncated]..." : text;
            
            String prompt = String.format(
                "Write a highly concise summary (maximum 2-3 sentences, bullet points are fine) of the key details extracted from the travel document/ticket \"%s\". " +
                "Only include important travel details like passenger name, flight number, check-in date/time, hotel name, booking reference, or travel source/destination. " +
                "Do not include greeting or conversational filler.\n\nDocument Content:\n%s", 
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
        
        // Fallback: simple snippet
        return text.length() > 250 ? text.substring(0, 250) + "..." : text;
    }
}
