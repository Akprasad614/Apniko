package com.localcart.service;

import com.localcart.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.UUID;

@Service
public class ProductImageStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 6000;
    private static final String PUBLIC_PREFIX = "/api/media/products/";

    private final Path storageRoot;

    public ProductImageStorageService(
            @Value("${apniko.image-storage-dir:uploads/products}") String storageDirectory) {
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a product image to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Product images must be 5 MB or smaller");
        }

        try {
            byte[] bytes = file.getBytes();
            String extension = detectImageExtension(bytes);
            validateImageDimensions(bytes);

            Files.createDirectories(storageRoot);
            String filename = UUID.randomUUID() + extension;
            Path target = storageRoot.resolve(filename).normalize();
            if (!target.getParent().equals(storageRoot)) {
                throw new BadRequestException("Invalid image filename");
            }

            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return PUBLIC_PREFIX + filename;
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store the product image", exception);
        }
    }

    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(PUBLIC_PREFIX)) {
            return;
        }

        String filename = imageUrl.substring(PUBLIC_PREFIX.length());
        if (!filename.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            return;
        }

        Path target = storageRoot.resolve(filename).normalize();
        if (!target.getParent().equals(storageRoot)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not remove the previous product image", exception);
        }
    }

    private String detectImageExtension(byte[] bytes) {
        boolean png = bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a;
        if (png) {
            return ".png";
        }

        boolean jpeg = bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff;
        if (jpeg) {
            return ".jpg";
        }

        throw new BadRequestException("Only valid JPEG and PNG product images are supported");
    }

    private void validateImageDimensions(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new BadRequestException("The uploaded file is not a readable image");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("The uploaded file is not a readable JPEG or PNG image");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1
                        || width > MAX_IMAGE_DIMENSION
                        || height > MAX_IMAGE_DIMENSION) {
                    throw new BadRequestException("Image dimensions must not exceed 6000 by 6000 pixels");
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
