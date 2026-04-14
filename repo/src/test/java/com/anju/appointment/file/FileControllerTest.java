package com.anju.appointment.file;

import com.anju.appointment.BaseIntegrationTest;
import com.anju.appointment.file.repository.FileRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest extends BaseIntegrationTest {

    @Autowired
    private FileRecordRepository fileRecordRepository;

    @Value("${app.storage.path}")
    private String storagePath;

    @BeforeEach
    void setUp() {
        fileRecordRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() throws IOException {
        Path storage = Paths.get(storagePath).toAbsolutePath().normalize();
        if (Files.exists(storage)) {
            Files.walkFileTree(storage, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Test
    void upload_storesFileAndReturnsMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-doc.pdf", "application/pdf",
                "PDF content here".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("module", "APPOINTMENT")
                        .param("referenceId", "1")
                        .param("description", "Test document")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName", is("test-doc.pdf")))
                .andExpect(jsonPath("$.contentType", is("application/pdf")))
                .andExpect(jsonPath("$.module", is("APPOINTMENT")))
                .andExpect(jsonPath("$.referenceId", is(1)))
                .andExpect(jsonPath("$.description", is("Test document")))
                .andExpect(jsonPath("$.fileSize").isNumber());
    }

    @Test
    void download_returnsCorrectFileBytes() throws Exception {
        byte[] content = "Hello file content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", content);

        String response = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("module", "PROPERTY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long fileId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"hello.txt\""))
                .andExpect(content().bytes(content));
    }

    @Test
    void listFiles_withFilters_works() throws Exception {
        // Upload two files to different modules
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "doc1.pdf", "application/pdf", "content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "doc2.pdf", "application/pdf", "content2".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                .file(file1)
                .param("module", "APPOINTMENT")
                .header("Authorization", "Bearer " + adminToken));

        mockMvc.perform(multipart("/api/files/upload")
                .file(file2)
                .param("module", "FINANCIAL")
                .header("Authorization", "Bearer " + adminToken));

        // Filter by module
        mockMvc.perform(get("/api/files")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("module", "APPOINTMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));

        // List all
        mockMvc.perform(get("/api/files")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void upload_sameFileTwice_createsVersionHistory() throws Exception {
        MockMultipartFile first = new MockMultipartFile(
                "file", "care-plan.pdf", "application/pdf", "version-one".getBytes());
        MockMultipartFile second = new MockMultipartFile(
                "file", "care-plan.pdf", "application/pdf", "version-two".getBytes());

        String firstResponse = mockMvc.perform(multipart("/api/files/upload")
                        .file(first)
                        .param("module", "APPOINTMENT")
                        .param("referenceId", "9")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)))
                .andReturn().getResponse().getContentAsString();

        Long firstId = objectMapper.readTree(firstResponse).get("id").asLong();

        mockMvc.perform(multipart("/api/files/upload")
                        .file(second)
                        .param("module", "APPOINTMENT")
                        .param("referenceId", "9")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$.parentFileId", is(firstId.intValue())));

        mockMvc.perform(get("/api/files/" + firstId + "/versions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].version", is(2)))
                .andExpect(jsonPath("$[1].version", is(1)));
    }
}
