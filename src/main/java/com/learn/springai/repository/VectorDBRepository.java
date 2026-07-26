package com.learn.springai.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VectorDBRepository {
    private final VectorStore vectorStore;

    public void saveToVectorDB(Resource pdfFile, String conversationID, String fileURI) {
        try {
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfFile);
            List<Document> pageDocuments = pdfReader.get();

            TextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(200)
                    .withMaxNumChunks(400)
                    .build();

            int chunkIndex = 0;
            List<Document> finalChunks = new ArrayList<>();

            for (int pageNumber = 0; pageNumber < pageDocuments.size(); pageNumber++) {
                List<Document> chunks = splitter.split(List.of(pageDocuments.get(pageNumber)));
                for (Document chunk : chunks) {
                    chunk.getMetadata().put("page_number", pageNumber + 1);
                    chunk.getMetadata().put("source", fileURI);
                    chunk.getMetadata().put("chunk_index", chunkIndex++);
                    chunk.getMetadata().put("conversation_id", conversationID.toString());
                }
                finalChunks.addAll(chunks);
            }

            vectorStore.add(finalChunks);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to save PDF to vector store", e);
        }
    }
}
