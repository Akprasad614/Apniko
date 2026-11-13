package com.localcart.service;

import com.localcart.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProductImageStorageServiceTest {

    @TempDir
    Path storageDirectory;

    @Test
    void storesAValidPngAndReturnsItsPublicMediaPath() throws Exception {
        ProductImageStorageService storage = new ProductImageStorageService(storageDirectory.toString());
        byte[] imageBytes = pngBytes();
        MockMultipartFile upload = new MockMultipartFile(
                "file", "product.png", "image/png", imageBytes
        );

        String url = storage.store(upload);

        assertTrue(url.matches("/api/media/products/[0-9a-fA-F-]{36}\\.png"));
        String filename = url.substring(url.lastIndexOf('/') + 1);
        Path storedFile = storageDirectory.resolve(filename);
        assertTrue(Files.exists(storedFile));
        assertArrayEquals(imageBytes, Files.readAllBytes(storedFile));

        storage.delete(url);
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void rejectsNonImageFilesBeforeWriting() {
        ProductImageStorageService storage = new ProductImageStorageService(storageDirectory.toString());
        MockMultipartFile upload = new MockMultipartFile(
                "file", "not-image.txt", "text/plain", "not an image".getBytes()
        );

        assertThrows(BadRequestException.class, () -> storage.store(upload));
        assertFalse(Files.exists(storageDirectory));
    }

    @Test
    void rejectsImagesLargerThanFiveMegabytes() {
        ProductImageStorageService storage = new ProductImageStorageService(storageDirectory.toString());
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile upload = new MockMultipartFile(
                "file", "large.png", "image/png", tooLarge
        );

        assertThrows(BadRequestException.class, () -> storage.store(upload));
        assertFalse(Files.exists(storageDirectory));
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
